package com.agenticmessenger.relay

import com.agenticmessenger.core.protocol.BlobMetadata
import com.agenticmessenger.core.protocol.KeyPackageRecord
import com.agenticmessenger.core.protocol.RelayFrame
import com.agenticmessenger.relay.queue.StoreAndForwardQueue
import com.agenticmessenger.relay.registry.KeyPackageRegistry
import com.agenticmessenger.relay.router.MailboxRouter
import com.agenticmessenger.relay.service.RelayService
import com.agenticmessenger.relay.storage.EncryptedBlobStorage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

class RelayServerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun testHealthAndStatsEndpoints(@TempDir tempDir: Path) {
        val dbFile = tempDir.resolve("test_health.db").toFile()
        val blobDir = tempDir.resolve("blobs").toFile()
        val queue = StoreAndForwardQueue("jdbc:sqlite:${dbFile.absolutePath}")
        val blobStorage = EncryptedBlobStorage(blobDir)
        val keyRegistry = KeyPackageRegistry("jdbc:sqlite:${dbFile.absolutePath}")
        val router = MailboxRouter()
        val relayService = RelayService(router, queue)

        try {
            testApplication {
                application {
                    configureRelayServer(
                        router = router,
                        queue = queue,
                        blobStorage = blobStorage,
                        keyRegistry = keyRegistry,
                        relayService = relayService
                    )
                }

                val healthResp = client.get("/health")
                assertEquals(HttpStatusCode.OK, healthResp.status)
                assertTrue(healthResp.bodyAsText().contains("\"status\":\"UP\""))

                val statsResp = client.get("/stats")
                assertEquals(HttpStatusCode.OK, statsResp.status)
                assertTrue(statsResp.bodyAsText().contains("activeMailboxes"))
            }
        } finally {
            queue.close()
            keyRegistry.close()
        }
    }

    @Test
    fun testEncryptedBlobLifecycleAndSha256(@TempDir tempDir: Path) {
        val dbFile = tempDir.resolve("test_blobs.db").toFile()
        val blobDir = tempDir.resolve("blobs").toFile()
        val queue = StoreAndForwardQueue("jdbc:sqlite:${dbFile.absolutePath}")
        val blobStorage = EncryptedBlobStorage(blobDir)
        val keyRegistry = KeyPackageRegistry("jdbc:sqlite:${dbFile.absolutePath}")
        val router = MailboxRouter()
        val relayService = RelayService(router, queue)

        try {
            testApplication {
                application {
                    configureRelayServer(
                        router = router,
                        queue = queue,
                        blobStorage = blobStorage,
                        keyRegistry = keyRegistry,
                        relayService = relayService
                    )
                }

                val testEncryptedAudio = "AES_GCM_ENCRYPTED_VOICE_NOTE_BYTES_1234567890".toByteArray(Charsets.UTF_8)

                // 1. Upload encrypted blob
                val uploadResp = client.post("/blobs") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(testEncryptedAudio)
                }
                assertEquals(HttpStatusCode.Created, uploadResp.status)
                val meta = json.decodeFromString<BlobMetadata>(uploadResp.bodyAsText())
                assertNotNull(meta.blobId)
                assertEquals(testEncryptedAudio.size.toLong(), meta.sizeBytes)

                // 2. Download and verify exact bytes and ETag
                val downloadResp = client.get(meta.downloadUrl)
                assertEquals(HttpStatusCode.OK, downloadResp.status)
                assertEquals(meta.sha256, downloadResp.headers[HttpHeaders.ETag])
                assertContentEquals(testEncryptedAudio, downloadResp.bodyAsBytes())

                // 3. Delete blob
                val delResp = client.delete(meta.downloadUrl)
                assertEquals(HttpStatusCode.NoContent, delResp.status)

                // 4. Verify gone
                val afterDel = client.get(meta.downloadUrl)
                assertEquals(HttpStatusCode.NotFound, afterDel.status)
            }
        } finally {
            queue.close()
            keyRegistry.close()
        }
    }

    @Test
    fun testKeyPackageRegistry(@TempDir tempDir: Path) {
        val dbFile = tempDir.resolve("test_keys.db").toFile()
        val blobDir = tempDir.resolve("blobs").toFile()
        val queue = StoreAndForwardQueue("jdbc:sqlite:${dbFile.absolutePath}")
        val blobStorage = EncryptedBlobStorage(blobDir)
        val keyRegistry = KeyPackageRegistry("jdbc:sqlite:${dbFile.absolutePath}")
        val router = MailboxRouter()
        val relayService = RelayService(router, queue)

        try {
            testApplication {
                application {
                    configureRelayServer(
                        router = router,
                        queue = queue,
                        blobStorage = blobStorage,
                        keyRegistry = keyRegistry,
                        relayService = relayService
                    )
                }

                val identityCommitment = "hash_did_aidos_jack_commitment_xyz"
                val record = KeyPackageRecord(
                    identityHash = identityCommitment,
                    deviceKeyId = "dev_jack_phone_01",
                    endpointType = "HUMAN_DIRECT",
                    publicKeyPackage = "BASE64_MLS_KEYPACKAGE_DATA_FOR_JACK_PHONE",
                    signatureHex = "abc123sig"
                )

                // Publish KeyPackage
                val postResp = client.post("/keys/$identityCommitment") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(record))
                }
                assertEquals(HttpStatusCode.Created, postResp.status)

                // Query KeyPackage
                val getResp = client.get("/keys/$identityCommitment")
                assertEquals(HttpStatusCode.OK, getResp.status)
                val list = json.decodeFromString<List<KeyPackageRecord>>(getResp.bodyAsText())
                assertEquals(1, list.size)
                assertEquals("dev_jack_phone_01", list[0].deviceKeyId)
                assertEquals("BASE64_MLS_KEYPACKAGE_DATA_FOR_JACK_PHONE", list[0].publicKeyPackage)

                // Revoke device
                val delResp = client.delete("/keys/$identityCommitment/dev_jack_phone_01")
                assertEquals(HttpStatusCode.NoContent, delResp.status)

                // Verify revoked
                val getAfterResp = client.get("/keys/$identityCommitment")
                val emptyList = json.decodeFromString<List<KeyPackageRecord>>(getAfterResp.bodyAsText())
                assertEquals(0, emptyList.size)
            }
        } finally {
            queue.close()
            keyRegistry.close()
        }
    }

    @Test
    fun testStoreAndForwardQueueDirectAndTtl(@TempDir tempDir: Path) {
        val dbFile = tempDir.resolve("test_queue.db").toFile()
        val queue = StoreAndForwardQueue("jdbc:sqlite:${dbFile.absolutePath}")

        try {
            val dest = "mailbox_bob_blind_token"
            val msgId1 = "msg_001"
            val msgId2 = "msg_002"

            // Enqueue 2 messages
            queue.enqueue(msgId1, dest, "mailbox_alice", "cipher_payload_1", ttlSeconds = 3600)
            queue.enqueue(msgId2, dest, "mailbox_alice", "cipher_payload_2", ttlSeconds = -1) // expired immediately

            // Purge expired
            val purged = queue.purgeExpired()
            assertEquals(1, purged) // msgId2 expired

            // Fetch pending
            val pending = queue.fetchPending(dest)
            assertEquals(1, pending.size)
            assertEquals(msgId1, pending[0].messageId)

            // Acknowledge -> immediate permanent deletion
            val acked = queue.acknowledge(msgId1, dest)
            assertTrue(acked)

            val pendingAfterAck = queue.fetchPending(dest)
            assertEquals(0, pendingAfterAck.size)
        } finally {
            queue.close()
        }
    }

    @Test
    fun testLiveWebSocketRoutingDirectAndOfflineQueue(@TempDir tempDir: Path) {
        val dbFile = tempDir.resolve("test_ws.db").toFile()
        val blobDir = tempDir.resolve("blobs").toFile()
        val queue = StoreAndForwardQueue("jdbc:sqlite:${dbFile.absolutePath}")
        val blobStorage = EncryptedBlobStorage(blobDir)
        val keyRegistry = KeyPackageRegistry("jdbc:sqlite:${dbFile.absolutePath}")
        val router = MailboxRouter()
        val relayService = RelayService(router, queue)

        try {
            testApplication {
                application {
                    configureRelayServer(
                        router = router,
                        queue = queue,
                        blobStorage = blobStorage,
                        keyRegistry = keyRegistry,
                        relayService = relayService
                    )
                }

                val wsClient = createClient {
                    install(WebSockets)
                }

                val aliceMailbox = "mb_alice_epoch1"
                val bobMailbox = "mb_bob_epoch1"

                // Scenario 1: Alice sends to Bob while Bob is OFFLINE
                wsClient.webSocket("/ws/relay") {
                    // Register Alice
                    send(Frame.Text(json.encodeToString<RelayFrame>(RelayFrame.Register(mailboxId = aliceMailbox))))
                    val regResp = (incoming.receive() as Frame.Text).readText()
                    val regFrame = json.decodeFromString<RelayFrame>(regResp)
                    assertTrue(regFrame is RelayFrame.Registered)
                    assertEquals(aliceMailbox, regFrame.mailboxId)

                    // Send message to offline Bob
                    val sendFrame = RelayFrame.Send(
                        messageId = "msg_offline_1",
                        destinationMailboxes = listOf(bobMailbox),
                        payloadCiphertext = "ENCRYPTED_FOR_BOB_WHILE_OFFLINE",
                        sourceMailbox = aliceMailbox
                    )
                    send(Frame.Text(json.encodeToString<RelayFrame>(sendFrame)))

                    val sentResp = (incoming.receive() as Frame.Text).readText()
                    val sentFrame = json.decodeFromString<RelayFrame>(sentResp)
                    assertTrue(sentFrame is RelayFrame.Sent)
                    assertEquals("msg_offline_1", sentFrame.messageId)
                    assertTrue(sentFrame.queuedRecipients.contains(bobMailbox))
                    assertEquals(0, sentFrame.directRecipients.size)
                }

                // Scenario 2: Bob connects now! He should automatically receive pending message
                wsClient.webSocket("/ws/relay") {
                    // Register Bob
                    send(Frame.Text(json.encodeToString<RelayFrame>(RelayFrame.Register(mailboxId = bobMailbox))))

                    // First receives pending offline delivery
                    val frame1 = (incoming.receive() as Frame.Text).readText()
                    val parsed1 = json.decodeFromString<RelayFrame>(frame1)

                    val deliverMsg: RelayFrame.Deliver
                    if (parsed1 is RelayFrame.Deliver) {
                        deliverMsg = parsed1
                        val frame2 = (incoming.receive() as Frame.Text).readText()
                        val parsed2 = json.decodeFromString<RelayFrame>(frame2)
                        assertTrue(parsed2 is RelayFrame.Registered)
                    } else {
                        assertTrue(parsed1 is RelayFrame.Registered)
                        val frame2 = (incoming.receive() as Frame.Text).readText()
                        deliverMsg = json.decodeFromString<RelayFrame>(frame2) as RelayFrame.Deliver
                    }

                    assertEquals("msg_offline_1", deliverMsg.messageId)
                    assertEquals("ENCRYPTED_FOR_BOB_WHILE_OFFLINE", deliverMsg.payloadCiphertext)

                    // Bob sends ACK to permanently wipe it from the relay queue
                    val ackFrame = RelayFrame.Ack(messageId = deliverMsg.messageId, mailboxId = bobMailbox)
                    send(Frame.Text(json.encodeToString<RelayFrame>(ackFrame)))

                    val ackConfirm = (incoming.receive() as Frame.Text).readText()
                    val ackParsed = json.decodeFromString<RelayFrame>(ackConfirm)
                    assertTrue(ackParsed is RelayFrame.AckConfirm)
                    assertEquals("msg_offline_1", ackParsed.messageId)

                    // Verify queue is now empty
                    assertEquals(0, queue.countPending(bobMailbox))
                }
            }
        } finally {
            queue.close()
            keyRegistry.close()
        }
    }

    @Test
    fun testS3BlobStorageWithVersityGW() {
        val s3Endpoint = "http://localhost:7070"
        val isVersityRunning = try {
            val url = java.net.URI.create(s3Endpoint).toURL()
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.responseCode > 0
        } catch (_: Exception) {
            false
        }

        if (!isVersityRunning) {
            println("VersityGW not reachable at $s3Endpoint, skipping S3 integration test.")
            return
        }

        val s3Config = com.agenticmessenger.relay.storage.S3StorageConfig(
            endpoint = s3Endpoint,
            bucket = "amp-test-blobs-${System.currentTimeMillis()}",
            accessKey = "ampaccesskey",
            secretKey = "ampsecretkey123",
            forcePathStyle = true
        )
        val s3Storage = com.agenticmessenger.relay.storage.S3BlobStorage(s3Config)

        try {
            val audioBytes = "ENCRYPTED_RED_PILL_AUDIO_BLOB_FOR_S3".toByteArray(Charsets.UTF_8)
            val metadata = s3Storage.store(audioBytes, preferredBlobId = "test_blob_s3_001")
            assertEquals("test_blob_s3_001", metadata.blobId)
            assertTrue(s3Storage.exists("test_blob_s3_001"))

            val retrieved = s3Storage.retrieve("test_blob_s3_001")
            assertNotNull(retrieved)
            assertContentEquals(audioBytes, retrieved.first)
            assertEquals(metadata.sha256, retrieved.second)

            val deleted = s3Storage.delete("test_blob_s3_001")
            assertTrue(deleted)
            assertFalse(s3Storage.exists("test_blob_s3_001"))
        } finally {
            s3Storage.close()
        }
    }
}
