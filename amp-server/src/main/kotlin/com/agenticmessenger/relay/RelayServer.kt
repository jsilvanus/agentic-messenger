package com.agenticmessenger.relay

import com.agenticmessenger.core.protocol.BlobMetadata
import com.agenticmessenger.core.protocol.KeyPackageRecord
import com.agenticmessenger.core.protocol.RelayFrame
import com.agenticmessenger.relay.pubsub.LocalPubSubBroker
import com.agenticmessenger.relay.pubsub.PubSubBroker
import com.agenticmessenger.relay.queue.SqliteStoreAndForwardQueue
import com.agenticmessenger.relay.queue.StoreAndForwardQueue
import com.agenticmessenger.relay.registry.KeyPackageRegistry
import com.agenticmessenger.relay.router.MailboxRouter
import com.agenticmessenger.relay.service.RelayService
import com.agenticmessenger.relay.storage.BlobStorage
import com.agenticmessenger.relay.storage.EncryptedBlobStorage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.core.readBytes
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.seconds

class RelayServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val dbPath: String = "blind_relay.db",
    val blobDir: File = File("data/blobs"),
    val purgeIntervalMs: Long = 60_000L
)

fun Application.configureRelayServer(
    router: MailboxRouter = MailboxRouter(),
    queue: StoreAndForwardQueue = SqliteStoreAndForwardQueue(),
    blobStorage: BlobStorage = EncryptedBlobStorage(File("data/blobs")),
    keyRegistry: KeyPackageRegistry = KeyPackageRegistry(),
    pubSubBroker: PubSubBroker? = null,
    relayService: RelayService = RelayService(router, queue, pubSubBroker),
    purgeIntervalMs: Long = 60_000L
) {
    val logger = LoggerFactory.getLogger("RelayServer")
    val startTime = System.currentTimeMillis()

    if (pubSubBroker != null && router.pubSubBroker == null) {
        router.pubSubBroker = pubSubBroker
    }

    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    install(ContentNegotiation) {
        json(json)
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ETag)
        allowHeader("X-Blob-SHA256")
    }

    install(WebSockets)

    // Launch background task to purge expired envelopes
    val purgeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    purgeScope.launch {
        while (isActive) {
            delay(purgeIntervalMs)
            try {
                val purged = queue.purgeExpired()
                if (purged > 0) {
                    logger.info("Purged {} expired store-and-forward envelopes.", purged)
                }
            } catch (e: Exception) {
                logger.error("Error during expired envelope sweep: {}", e.message)
            }
        }
    }

    routing {
        // Health check endpoint
        get("/health") {
            val uptime = System.currentTimeMillis() - startTime
            call.respond(
                mapOf(
                    "status" to "UP",
                    "uptimeMs" to uptime.toString(),
                    "version" to "0.1.0"
                )
            )
        }

        // Metrics and runtime stats
        get("/stats") {
            call.respond(
                mapOf(
                    "activeMailboxes" to router.activeMailboxCount(),
                    "activeConnections" to router.activeConnectionCount(),
                    "totalQueuedMessages" to queue.totalQueueSize(),
                    "totalBlobs" to blobStorage.countBlobs()
                )
            )
        }

        // Blind Relay WebSocket endpoint
        webSocket("/ws/relay") {
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            try {
                                val relayFrame = json.decodeFromString<RelayFrame>(text)
                                val response = relayService.handleFrame(this, relayFrame)
                                if (response != null) {
                                    send(Frame.Text(json.encodeToString<RelayFrame>(response)))
                                }
                            } catch (e: Exception) {
                                logger.error("Failed to parse incoming frame: {}", e.message)
                                val err = RelayFrame.Error(
                                    code = "FRAME_PARSE_ERROR",
                                    message = e.message ?: "Invalid frame JSON"
                                )
                                send(Frame.Text(json.encodeToString<RelayFrame>(err)))
                            }
                        }
                        is Frame.Ping -> {
                            send(Frame.Pong(frame.data))
                        }
                        else -> {
                            // ignore binary or other frames
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("WebSocket connection terminated: {}", e.message)
            } finally {
                relayService.onSessionDisconnected(this)
            }
        }

        // ==========================================
        // Client-Encrypted Blob Storage Endpoints
        // ==========================================
        route("/blobs") {
            // Upload blob with auto-generated ID
            post {
                try {
                    val bytes = call.receive<ByteArray>()
                    val preferredId = call.request.queryParameters["id"]
                    val expectedSha256 = call.request.header("X-Blob-SHA256")
                    val metadata = blobStorage.store(
                        bytes = bytes,
                        preferredBlobId = preferredId,
                        expectedSha256 = expectedSha256
                    )
                    call.response.header(HttpHeaders.ETag, metadata.sha256)
                    call.respond(HttpStatusCode.Created, metadata)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid blob")))
                }
            }

            // Upload blob with specific ID
            put("/{blobId}") {
                val blobId = call.parameters["blobId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                try {
                    val bytes = call.receive<ByteArray>()
                    val expectedSha256 = call.request.header("X-Blob-SHA256")
                    val metadata = blobStorage.store(
                        bytes = bytes,
                        preferredBlobId = blobId,
                        expectedSha256 = expectedSha256
                    )
                    call.response.header(HttpHeaders.ETag, metadata.sha256)
                    call.respond(HttpStatusCode.OK, metadata)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid blob")))
                }
            }

            // Download encrypted ciphertext blob
            get("/{blobId}") {
                val blobId = call.parameters["blobId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val retrieved = blobStorage.retrieve(blobId)
                if (retrieved == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Blob not found"))
                } else {
                    val (bytes, sha256) = retrieved
                    call.response.header(HttpHeaders.ETag, sha256)
                    call.respondBytes(bytes, ContentType.Application.OctetStream, HttpStatusCode.OK)
                }
            }

            // Delete blob
            delete("/{blobId}") {
                val blobId = call.parameters["blobId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val deleted = blobStorage.delete(blobId)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        // ==========================================
        // Public KeyPackage Registry Endpoints
        // ==========================================
        route("/keys") {
            post("/{identityHash}") {
                val identityHash = call.parameters["identityHash"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val record = call.receive<KeyPackageRecord>()
                if (record.identityHash != identityHash) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Identity hash mismatch"))
                }
                val saved = keyRegistry.publishKeyPackage(record)
                if (saved) {
                    call.respond(HttpStatusCode.Created, mapOf("status" to "OK", "identityHash" to identityHash))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to save key package"))
                }
            }

            get("/{identityHash}") {
                val identityHash = call.parameters["identityHash"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val packages = keyRegistry.getKeyPackages(identityHash)
                call.respond(HttpStatusCode.OK, packages)
            }

            delete("/{identityHash}/{deviceKeyId}") {
                val identityHash = call.parameters["identityHash"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val deviceKeyId = call.parameters["deviceKeyId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val revoked = keyRegistry.revokeDeviceKey(identityHash, deviceKeyId)
                if (revoked) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
