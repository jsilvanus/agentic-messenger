package com.agenticmessenger.client

import com.agenticmessenger.client.identity.ClientIdentity
import com.agenticmessenger.client.model.MessageDirection
import com.agenticmessenger.client.model.MessageStatus
import com.agenticmessenger.client.network.AmpConnectionState
import com.agenticmessenger.client.storage.SqliteStorage
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
import kotlin.test.assertNotNull

class AmpSessionTest {

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
        val dbFile = tempDir.resolve("test_session_relay.db").toFile()
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
    fun testTwoSessionsEndToEndWithLocalStorage() = runBlocking {
        val aliceIdentity = ClientIdentity.generate("alice_session_dev", "did:aidos:alice")
        val bobIdentity = ClientIdentity.generate("bob_session_dev", "did:aidos:bob")

        val wsUrl = "ws://127.0.0.1:$port/ws/relay"

        val aliceStorage = SqliteStorage.inMemory()
        val bobStorage = SqliteStorage.inMemory()

        val aliceSession = AmpSession(identity = aliceIdentity, storage = aliceStorage, relayWsUrl = wsUrl)
        val bobSession = AmpSession(identity = bobIdentity, storage = bobStorage, relayWsUrl = wsUrl)

        try {
            aliceSession.start()
            bobSession.start()

            withTimeout(5000) {
                aliceSession.connectionState.first { it == AmpConnectionState.CONNECTED }
                bobSession.connectionState.first { it == AmpConnectionState.CONNECTED }
            }

            val chatText = "Hey Bob! This message is coordinated through AmpSession and stored in SQLite."

            val sentMsg = aliceSession.sendMessage(
                recipientMailboxToken = bobIdentity.mailboxToken,
                recipientPrincipalId = bobIdentity.principalId,
                recipientX25519PublicKeyHex = bobIdentity.x25519PublicKeyHex,
                plaintext = chatText,
                policy = PrivacyLevel.HUMAN_ONLY
            )

            assertEquals(MessageDirection.OUTBOUND, sentMsg.direction)

            // Bob observes conversation flow
            val bobConversationFlow = bobSession.observeConversation(aliceIdentity.mailboxToken)

            val bobMessages = withTimeout(5000) {
                bobConversationFlow.first { list -> list.isNotEmpty() }
            }

            assertEquals(1, bobMessages.size)
            val received = bobMessages[0]
            assertEquals(chatText, received.plaintext)
            assertEquals(MessageDirection.INBOUND, received.direction)
            assertEquals(MessageStatus.DELIVERED, received.status)

            // Verify stored in Bob's SQLite database directly
            val persistedInBobDb = bobStorage.messageRepository.getMessageById(sentMsg.id)
            assertNotNull(persistedInBobDb)
            assertEquals(chatText, persistedInBobDb.plaintext)

        } finally {
            aliceSession.close()
            bobSession.close()
        }
    }
}
