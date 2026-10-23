package com.agenticmessenger.relay

import com.agenticmessenger.core.protocol.RelayFrame
import com.agenticmessenger.relay.pubsub.LocalPubSubBroker
import com.agenticmessenger.relay.pubsub.RedisPubSubBroker
import com.agenticmessenger.relay.queue.RedisStoreAndForwardQueue
import com.agenticmessenger.relay.queue.SqliteStoreAndForwardQueue
import com.agenticmessenger.relay.registry.KeyPackageRegistry
import com.agenticmessenger.relay.router.MailboxRouter
import com.agenticmessenger.relay.service.RelayService
import com.agenticmessenger.relay.storage.EncryptedBlobStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir
import redis.clients.jedis.Jedis
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import kotlin.test.*

class CrossNodeRelayTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private data class RelayNodeHandle(
        val server: EmbeddedServer<*, *>,
        val port: Int,
        val router: MailboxRouter,
        val keyRegistry: KeyPackageRegistry
    ) : Closeable {
        override fun close() {
            server.stop(500, 1000)
            router.close()
            keyRegistry.close()
        }
    }

    private fun createRelayNode(
        port: Int = 0,
        tempDir: Path,
        nodeName: String,
        sharedQueue: SqliteStoreAndForwardQueue,
        sharedBroker: LocalPubSubBroker
    ): RelayNodeHandle {
        val blobDir = tempDir.resolve("blobs_$nodeName").toFile()
        val keyDb = tempDir.resolve("keys_$nodeName.db").toFile()
        val blobStorage = EncryptedBlobStorage(blobDir)
        val keyRegistry = KeyPackageRegistry("jdbc:sqlite:${keyDb.absolutePath}")
        val router = MailboxRouter(sharedBroker)
        val relayService = RelayService(router, sharedQueue, sharedBroker)

        val server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            configureRelayServer(
                router = router,
                queue = sharedQueue,
                blobStorage = blobStorage,
                keyRegistry = keyRegistry,
                pubSubBroker = sharedBroker,
                relayService = relayService
            )
        }.start(wait = false)

        val actualPort = runBlocking {
            server.engine.resolvedConnectors().first().port
        }
        return RelayNodeHandle(server, actualPort, router, keyRegistry)
    }

    @Test
    fun testCrossNodeRealtimeDispatch(@TempDir tempDir: Path) = runBlocking {
        val sharedDbFile = tempDir.resolve("shared_queue.db").toFile()
        val sharedQueue = SqliteStoreAndForwardQueue("jdbc:sqlite:${sharedDbFile.absolutePath}")
        val sharedBroker = LocalPubSubBroker()

        val node1 = createRelayNode(0, tempDir, "node1", sharedQueue, sharedBroker)
        val node2 = createRelayNode(0, tempDir, "node2", sharedQueue, sharedBroker)

        val client1 = HttpClient(CIO) { install(WebSockets) }
        val client2 = HttpClient(CIO) { install(WebSockets) }

        val aliceMailbox = "mb_alice_crossnode"
        val bobMailbox = "mb_bob_crossnode"
        val messageId = "msg_cross_node_001"
        val secretPayload = "ENCRYPTED_MLS_CIPHERTEXT_FROM_ALICE_TO_BOB"

        try {
            // Bob connects to Node 2 and registers
            client2.webSocket(host = "127.0.0.1", port = node2.port, path = "/ws/relay") {
                send(Frame.Text(json.encodeToString<RelayFrame>(RelayFrame.Register(mailboxId = bobMailbox))))
                val bobReg = (incoming.receive() as Frame.Text).readText()
                val bobRegFrame = json.decodeFromString<RelayFrame>(bobReg)
                assertTrue(bobRegFrame is RelayFrame.Registered)
                assertEquals(bobMailbox, bobRegFrame.mailboxId)

                // Alice connects to Node 1 and registers
                client1.webSocket(host = "127.0.0.1", port = node1.port, path = "/ws/relay") {
                    send(Frame.Text(json.encodeToString<RelayFrame>(RelayFrame.Register(mailboxId = aliceMailbox))))
                    val aliceReg = (incoming.receive() as Frame.Text).readText()
                    assertTrue(json.decodeFromString<RelayFrame>(aliceReg) is RelayFrame.Registered)

                    // Alice on Node 1 sends message to Bob on Node 2
                    val sendFrame = RelayFrame.Send(
                        messageId = messageId,
                        destinationMailboxes = listOf(bobMailbox),
                        payloadCiphertext = secretPayload,
                        sourceMailbox = aliceMailbox
                    )
                    send(Frame.Text(json.encodeToString<RelayFrame>(sendFrame)))

                    // Alice gets confirmation that Bob received directly (real-time dispatch)
                    val sentResp = (incoming.receive() as Frame.Text).readText()
                    val sentFrame = json.decodeFromString<RelayFrame>(sentResp)
                    assertTrue(sentFrame is RelayFrame.Sent)
                    assertEquals(messageId, sentFrame.messageId)
                    assertTrue(sentFrame.directRecipients.contains(bobMailbox))
                    assertEquals(0, sentFrame.queuedRecipients.size)
                }

                // Bob on Node 2 receives DELIVER frame in real time!
                withTimeout(5000) {
                    val bobDeliverText = (incoming.receive() as Frame.Text).readText()
                    val bobDeliverFrame = json.decodeFromString<RelayFrame>(bobDeliverText)
                    assertTrue(bobDeliverFrame is RelayFrame.Deliver)
                    assertEquals(messageId, bobDeliverFrame.messageId)
                    assertEquals(secretPayload, bobDeliverFrame.payloadCiphertext)
                    assertEquals(aliceMailbox, bobDeliverFrame.sourceMailbox)
                    assertEquals(bobMailbox, bobDeliverFrame.destinationMailbox)

                    // Bob sends ACK to Node 2
                    val ackFrame = RelayFrame.Ack(messageId = messageId, mailboxId = bobMailbox)
                    send(Frame.Text(json.encodeToString<RelayFrame>(ackFrame)))

                    val ackConfirmText = (incoming.receive() as Frame.Text).readText()
                    val ackConfirmFrame = json.decodeFromString<RelayFrame>(ackConfirmText)
                    assertTrue(ackConfirmFrame is RelayFrame.AckConfirm)
                    assertEquals(messageId, ackConfirmFrame.messageId)
                }

                // Verify the message was permanently purged from the shared queue upon ACK
                assertEquals(0, sharedQueue.countPending(bobMailbox))
            }
        } finally {
            client1.close()
            client2.close()
            node1.close()
            node2.close()
            sharedBroker.close()
            sharedQueue.close()
        }
    }

    @Test
    fun testCrossNodeStoreAndForwardOfflineQueue(@TempDir tempDir: Path) = runBlocking {
        val sharedDbFile = tempDir.resolve("shared_offline_queue.db").toFile()
        val sharedQueue = SqliteStoreAndForwardQueue("jdbc:sqlite:${sharedDbFile.absolutePath}")
        val sharedBroker = LocalPubSubBroker()

        val node1 = createRelayNode(0, tempDir, "node1_sf", sharedQueue, sharedBroker)
        val node2 = createRelayNode(0, tempDir, "node2_sf", sharedQueue, sharedBroker)

        val client1 = HttpClient(CIO) { install(WebSockets) }
        val client2 = HttpClient(CIO) { install(WebSockets) }

        val aliceMailbox = "mb_alice_sf"
        val bobMailbox = "mb_bob_sf"
        val messageId = "msg_offline_cross_002"
        val secretPayload = "OFFLINE_PAYLOAD_FOR_BOB_WAITING_ON_SHARED_QUEUE"

        try {
            // 1. Bob is offline. Alice connects to Node 1 and sends to Bob
            client1.webSocket(host = "127.0.0.1", port = node1.port, path = "/ws/relay") {
                send(Frame.Text(json.encodeToString<RelayFrame>(RelayFrame.Register(mailboxId = aliceMailbox))))
                val regText = (incoming.receive() as Frame.Text).readText()
                assertTrue(json.decodeFromString<RelayFrame>(regText) is RelayFrame.Registered)

                val sendFrame = RelayFrame.Send(
                    messageId = messageId,
                    destinationMailboxes = listOf(bobMailbox),
                    payloadCiphertext = secretPayload,
                    sourceMailbox = aliceMailbox
                )
                send(Frame.Text(json.encodeToString<RelayFrame>(sendFrame)))

                val sentResp = (incoming.receive() as Frame.Text).readText()
                val sentFrame = json.decodeFromString<RelayFrame>(sentResp)
                assertTrue(sentFrame is RelayFrame.Sent)
                assertEquals(messageId, sentFrame.messageId)
                assertEquals(0, sentFrame.directRecipients.size)
                assertTrue(sentFrame.queuedRecipients.contains(bobMailbox))
            }

            // Verify message is pending in the shared queue
            assertEquals(1, sharedQueue.countPending(bobMailbox))

            // 2. Bob reconnects to Node 2! He receives the pending message automatically
            client2.webSocket(host = "127.0.0.1", port = node2.port, path = "/ws/relay") {
                send(Frame.Text(json.encodeToString<RelayFrame>(RelayFrame.Register(mailboxId = bobMailbox))))

                val frame1Text = (incoming.receive() as Frame.Text).readText()
                val parsed1 = json.decodeFromString<RelayFrame>(frame1Text)

                val deliverMsg: RelayFrame.Deliver
                if (parsed1 is RelayFrame.Deliver) {
                    deliverMsg = parsed1
                    val frame2Text = (incoming.receive() as Frame.Text).readText()
                    assertTrue(json.decodeFromString<RelayFrame>(frame2Text) is RelayFrame.Registered)
                } else {
                    assertTrue(parsed1 is RelayFrame.Registered)
                    val frame2Text = (incoming.receive() as Frame.Text).readText()
                    deliverMsg = json.decodeFromString<RelayFrame>(frame2Text) as RelayFrame.Deliver
                }

                assertEquals(messageId, deliverMsg.messageId)
                assertEquals(secretPayload, deliverMsg.payloadCiphertext)
                assertEquals(aliceMailbox, deliverMsg.sourceMailbox)

                // Bob sends ACK to Node 2 -> purges from shared queue
                val ackFrame = RelayFrame.Ack(messageId = messageId, mailboxId = bobMailbox)
                send(Frame.Text(json.encodeToString<RelayFrame>(ackFrame)))

                val ackConfirmText = (incoming.receive() as Frame.Text).readText()
                val ackConfirm = json.decodeFromString<RelayFrame>(ackConfirmText)
                assertTrue(ackConfirm is RelayFrame.AckConfirm)
                assertEquals(messageId, ackConfirm.messageId)

                // Verify purged from shared queue
                assertEquals(0, sharedQueue.countPending(bobMailbox))
            }
        } finally {
            client1.close()
            client2.close()
            node1.close()
            node2.close()
            sharedBroker.close()
            sharedQueue.close()
        }
    }

    @Test
    fun testRedisBackendIfRunning() = runBlocking {
        val isRedisRunning = try {
            val jedis = Jedis("localhost", 6379)
            val pong = jedis.ping()
            jedis.close()
            pong == "PONG"
        } catch (_: Exception) {
            false
        }

        if (!isRedisRunning) {
            println("Redis not reachable on localhost:6379, skipping Redis backend integration test.")
            return@runBlocking
        }

        println("Redis is running! Testing RedisStoreAndForwardQueue and RedisPubSubBroker...")
        val queue = RedisStoreAndForwardQueue("redis://localhost:6379")
        val broker = RedisPubSubBroker("redis://localhost:6379")

        try {
            val destMailbox = "mb_redis_test_${System.currentTimeMillis()}"
            val msgId = "msg_redis_1"
            val payload = "CIPHERTEXT_REDIS_QUEUE"

            // 1. Enqueue
            val enqueued = queue.enqueue(msgId, destMailbox, "mb_sender", payload, 3600)
            assertTrue(enqueued)
            assertEquals(1, queue.countPending(destMailbox))

            // 2. Fetch
            val pending = queue.fetchPending(destMailbox)
            assertEquals(1, pending.size)
            assertEquals(msgId, pending[0].messageId)
            assertEquals(payload, pending[0].payloadCiphertext)

            // 3. Acknowledge -> purge
            val acked = queue.acknowledge(msgId, destMailbox)
            assertTrue(acked)
            assertEquals(0, queue.countPending(destMailbox))

            // 4. Test Redis PubSub
            var receivedPayload: String? = null
            val sub = broker.subscribe(destMailbox) { _, p ->
                receivedPayload = p
            }

            // Small delay for Redis subscription propagation
            kotlinx.coroutines.delay(200)

            val publishedCount = broker.publish(destMailbox, "REALTIME_REDIS_PAYLOAD")
            assertTrue(publishedCount > 0)

            kotlinx.coroutines.delay(200)
            assertEquals("REALTIME_REDIS_PAYLOAD", receivedPayload)

            sub.close()
        } finally {
            queue.close()
            broker.close()
        }
    }
}
