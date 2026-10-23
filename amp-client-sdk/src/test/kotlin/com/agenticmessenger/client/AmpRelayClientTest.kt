package com.agenticmessenger.client

import com.agenticmessenger.client.identity.ClientIdentity
import com.agenticmessenger.client.network.AmpConnectionState
import com.agenticmessenger.client.network.AmpRelayClient
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.relay.configureRelayServer
import com.agenticmessenger.relay.queue.SqliteStoreAndForwardQueue
import com.agenticmessenger.relay.registry.KeyPackageRegistry
import com.agenticmessenger.relay.router.MailboxRouter
import com.agenticmessenger.relay.service.RelayService
import com.agenticmessenger.relay.storage.EncryptedBlobStorage
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmpRelayClientTest {

    private var server: EmbeddedServer<*, *>? = null
    private var port: Int = 0
    private var queue: SqliteStoreAndForwardQueue? = null
    private var keyRegistry: KeyPackageRegistry? = null

    private fun findFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        port = findFreePort()
        val dbFile = tempDir.resolve("test_client_relay.db").toFile()
        val blobDir = tempDir.resolve("blobs").toFile()
        blobDir.mkdirs()

        val q = SqliteStoreAndForwardQueue("jdbc:sqlite:${dbFile.absolutePath}")
        val blobStorage = EncryptedBlobStorage(blobDir)
        val kr = KeyPackageRegistry("jdbc:sqlite:${dbFile.absolutePath}")
        val router = MailboxRouter()
        val relayService = RelayService(router, q)

        queue = q
        keyRegistry = kr

        server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            configureRelayServer(
                router = router,
                queue = q,
                blobStorage = blobStorage,
                keyRegistry = kr,
                relayService = relayService
            )
        }.start(wait = false)

        Thread.sleep(800)
    }

    @AfterEach
    fun tearDown() {
        server?.stop(500, 1000)
        queue?.close()
        keyRegistry?.close()
    }

    @Test
    fun testAliceAndBobRealTimeEncryptedChat() = runBlocking {
        val aliceIdentity = ClientIdentity.generate("alice_dev_1", "did:aidos:alice")
        val bobIdentity = ClientIdentity.generate("bob_dev_1", "did:aidos:bob")

        val wsUrl = "ws://127.0.0.1:$port/ws/relay"

        val aliceClient = AmpRelayClient(serverWsUrl = wsUrl, identity = aliceIdentity)
        val bobClient = AmpRelayClient(serverWsUrl = wsUrl, identity = bobIdentity)

        try {
            aliceClient.connect()
            bobClient.connect()

            withTimeout(5000) {
                aliceClient.connectionState.first { it == AmpConnectionState.CONNECTED }
                bobClient.connectionState.first { it == AmpConnectionState.CONNECTED }
            }

            assertEquals(AmpConnectionState.CONNECTED, aliceClient.connectionState.value)
            assertEquals(AmpConnectionState.CONNECTED, bobClient.connectionState.value)

            val secretText = "Hello Bob! This message is end-to-end encrypted via X25519 & AES-256-GCM."

            val messageId = aliceClient.sendDirectMessage(
                recipientMailboxToken = bobIdentity.mailboxToken,
                recipientPrincipalId = bobIdentity.principalId,
                recipientX25519PublicKeyHex = bobIdentity.x25519PublicKeyHex,
                plaintext = secretText,
                policy = PrivacyLevel.HUMAN_ONLY
            )

            // Bob receives the message, decrypts it, and asserts content
            val received = withTimeout(5000) {
                bobClient.incomingMessages.first()
            }

            assertEquals(messageId, received.messageId)
            assertEquals(secretText, received.plaintext)
            assertEquals(PrivacyLevel.HUMAN_ONLY, received.policy)
            assertEquals(aliceIdentity.mailboxToken, received.sourceMailbox)

            // Verify queue is automatically purged after delivery ACK
            delay(500)
            val pendingCount = queue?.countPending(bobIdentity.mailboxToken) ?: 0
            assertEquals(0, pendingCount, "Queue must be purged after ACK")

        } finally {
            aliceClient.close()
            bobClient.close()
        }
    }

    @Test
    fun testOfflineStoreAndForwardDelivery() = runBlocking {
        val aliceIdentity = ClientIdentity.generate("alice_dev_2", "did:aidos:alice")
        val charlieIdentity = ClientIdentity.generate("charlie_dev_1", "did:aidos:charlie")

        val wsUrl = "ws://127.0.0.1:$port/ws/relay"
        val aliceClient = AmpRelayClient(serverWsUrl = wsUrl, identity = aliceIdentity)

        try {
            aliceClient.connect()
            withTimeout(5000) {
                aliceClient.connectionState.first { it == AmpConnectionState.CONNECTED }
            }

            val offlineText = "Charlie, this was sent while you were completely offline!"

            val msgId = aliceClient.sendDirectMessage(
                recipientMailboxToken = charlieIdentity.mailboxToken,
                recipientPrincipalId = charlieIdentity.principalId,
                recipientX25519PublicKeyHex = charlieIdentity.x25519PublicKeyHex,
                plaintext = offlineText
            )

            // Verify message is pending in queue
            delay(500)
            val pendingBefore = queue?.countPending(charlieIdentity.mailboxToken) ?: 0
            assertEquals(1, pendingBefore, "Offline message must be safely queued in store-and-forward")

            // Now Charlie connects!
            val charlieClient = AmpRelayClient(serverWsUrl = wsUrl, identity = charlieIdentity)
            try {
                charlieClient.connect()

                val received = withTimeout(5000) {
                    charlieClient.incomingMessages.first()
                }

                assertEquals(msgId, received.messageId)
                assertEquals(offlineText, received.plaintext)

                // Verify purged upon ACK
                delay(500)
                val pendingAfter = queue?.countPending(charlieIdentity.mailboxToken) ?: 0
                assertEquals(0, pendingAfter, "Queue must be purged once delivered and ACKed")
            } finally {
                charlieClient.close()
            }

        } finally {
            aliceClient.close()
        }
    }
}
