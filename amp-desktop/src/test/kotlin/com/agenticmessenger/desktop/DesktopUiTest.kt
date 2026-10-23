package com.agenticmessenger.desktop

import com.agenticmessenger.desktop.bridge.InMemoryDesktopLoopbackBridge
import com.agenticmessenger.desktop.model.ConnectionStatus
import com.agenticmessenger.desktop.model.DesktopIdentity
import com.agenticmessenger.desktop.model.DesktopWindowManager
import com.agenticmessenger.ui.model.MeshProvenanceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopUiTest {

    @Test
    fun testDesktopIdentitiesAndMailboxGeneration() {
        val alice = DesktopIdentity.ALICE
        val bob = DesktopIdentity.BOB

        assertEquals("Alice", alice.displayName)
        assertEquals("Bob", bob.displayName)
        assertTrue(alice.mailboxId.isNotEmpty(), "Alice mailbox ID must not be empty")
        assertTrue(bob.mailboxId.isNotEmpty(), "Bob mailbox ID must not be empty")
        assertTrue(alice.mailboxId != bob.mailboxId, "Mailbox IDs must be distinct")

        // Custom identity
        val charlie = DesktopIdentity.create("charlie", "Charlie", "did:aidos:charlie", "key_charlie_01")
        assertEquals("Charlie", charlie.displayName)
        assertTrue(charlie.mailboxId.isNotEmpty())
    }

    @Test
    fun testDualWindowWindowManagerLifecycle() {
        val bridge = InMemoryDesktopLoopbackBridge()
        val windowManager = DesktopWindowManager(bridge)

        assertEquals(0, windowManager.activeSessions.size)

        // Launch Alice and Bob
        windowManager.launchAliceAndBob()
        assertEquals(2, windowManager.activeSessions.size)

        val aliceSession = windowManager.activeSessions.find { it.id == "session_alice" }
        val bobSession = windowManager.activeSessions.find { it.id == "session_bob" }
        assertNotNull(aliceSession)
        assertNotNull(bobSession)

        assertEquals("Alice", aliceSession.identity.displayName)
        assertEquals("Bob", aliceSession.targetPeer.displayName)
        assertEquals("Bob", bobSession.identity.displayName)
        assertEquals("Alice", bobSession.targetPeer.displayName)

        // Close one window
        windowManager.closeSession("session_alice")
        assertEquals(1, windowManager.activeSessions.size)
        assertNotNull(windowManager.activeSessions.find { it.id == "session_bob" })

        // Close all
        windowManager.closeAll()
        assertEquals(0, windowManager.activeSessions.size)
    }

    @Test
    fun testDualWindowEndToEndMessagingInSingleProcess() {
        val bridge = InMemoryDesktopLoopbackBridge()
        val windowManager = DesktopWindowManager(bridge)
        windowManager.launchAliceAndBob()

        val alice = windowManager.activeSessions.first { it.id == "session_alice" }
        val bob = windowManager.activeSessions.first { it.id == "session_bob" }

        assertEquals(0, alice.messages.size)
        assertEquals(0, bob.messages.size)

        // 1. Alice types and sends message to Bob
        alice.inputDraft = "Hello Bob! Testing from Window A."
        alice.sendMessage()

        // Alice's outbox feed
        assertEquals(1, alice.messages.size)
        val aliceSent = alice.messages.first()
        assertTrue(aliceSent.isSentByMe)
        assertTrue(aliceSent.isDelivered)
        assertEquals("Hello Bob! Testing from Window A.", aliceSent.text)
        assertEquals(MeshProvenanceType.HUMAN_DIRECT, aliceSent.provenanceType)
        assertEquals("", alice.inputDraft, "Input draft should be cleared after send")

        // Bob's inbox feed (instant local loopback delivery)
        assertEquals(1, bob.messages.size)
        val bobReceived = bob.messages.first()
        assertFalse(bobReceived.isSentByMe)
        assertEquals("Hello Bob! Testing from Window A.", bobReceived.text)
        assertEquals("Alice", bobReceived.senderDisplayName)

        // 2. Bob replies to Alice
        bob.inputDraft = "Got it Alice! Replying from Window B."
        bob.sendMessage()

        // Bob has 2 messages (1 received, 1 sent)
        assertEquals(2, bob.messages.size)
        val bobSent = bob.messages.last()
        assertTrue(bobSent.isSentByMe)

        // Alice receives Bob's reply
        assertEquals(2, alice.messages.size)
        val aliceReceived = alice.messages.last()
        assertFalse(aliceReceived.isSentByMe)
        assertEquals("Got it Alice! Replying from Window B.", aliceReceived.text)
        assertEquals("Bob", aliceReceived.senderDisplayName)
    }

    @Test
    fun testHumanOnlyModeMessaging() {
        val bridge = InMemoryDesktopLoopbackBridge()
        val windowManager = DesktopWindowManager(bridge)
        windowManager.launchAliceAndBob()

        val alice = windowManager.activeSessions.first { it.id == "session_alice" }
        val bob = windowManager.activeSessions.first { it.id == "session_bob" }

        // Enable human-only lock
        alice.toggleHumanOnly()
        assertTrue(alice.isHumanOnly)

        alice.inputDraft = "Top secret human-only passcode: 8821"
        alice.sendMessage()

        val aliceMsg = alice.messages.first()
        assertTrue(aliceMsg.isHumanOnly)
        assertEquals(MeshProvenanceType.HUMAN_ONLY_E2EE, aliceMsg.provenanceType)

        val bobMsg = bob.messages.first()
        assertTrue(bobMsg.isHumanOnly)
        assertEquals(MeshProvenanceType.HUMAN_ONLY_E2EE, bobMsg.provenanceType)
    }

    @Test
    fun testConnectionStatusToggling() {
        val bridge = InMemoryDesktopLoopbackBridge()
        val windowManager = DesktopWindowManager(bridge)
        windowManager.launchAliceAndBob()

        val alice = windowManager.activeSessions.first { it.id == "session_alice" }
        val bob = windowManager.activeSessions.first { it.id == "session_bob" }

        assertEquals(ConnectionStatus.CONNECTED, bob.connectionStatus)

        // Toggle Bob offline
        bob.toggleConnection()
        assertEquals(ConnectionStatus.OFFLINE, bob.connectionStatus)

        // Alice sends message while Bob is offline
        alice.inputDraft = "Hey Bob are you there?"
        alice.sendMessage()

        val sentMsg = alice.messages.first()
        assertFalse(sentMsg.isDelivered, "Message should be marked undelivered when recipient is offline")
        assertEquals(0, bob.messages.size, "Bob should not receive messages when offline")
    }
}
