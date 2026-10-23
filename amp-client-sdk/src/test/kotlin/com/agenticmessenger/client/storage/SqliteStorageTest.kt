package com.agenticmessenger.client.storage

import com.agenticmessenger.client.model.MessageDirection
import com.agenticmessenger.client.model.MessageStatus
import com.agenticmessenger.client.model.OutboxItem
import com.agenticmessenger.client.model.StoredMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SqliteStorageTest {

    private lateinit var storage: SqliteStorage
    private lateinit var messageRepo: SqliteMessageRepository
    private lateinit var outboxQueue: SqliteOutboxQueue

    @BeforeTest
    fun setUp() {
        storage = SqliteStorage.inMemory()
        messageRepo = storage.messageRepository
        outboxQueue = storage.outboxQueue
    }

    @AfterTest
    fun tearDown() {
        storage.close()
    }

    @Test
    fun testSaveAndRetrieveMessagesChronologicalOrder() = runTest {
        val conversationId = "conv-alice-bob"
        val msg1 = StoredMessage(
            id = "msg-1",
            conversationId = conversationId,
            senderId = "alice",
            recipientId = "bob",
            plaintext = "First message",
            direction = MessageDirection.OUTBOUND,
            status = MessageStatus.DELIVERED,
            timestampMillis = 1000L
        )
        val msg2 = StoredMessage(
            id = "msg-2",
            conversationId = conversationId,
            senderId = "bob",
            recipientId = "alice",
            plaintext = "Second message",
            direction = MessageDirection.INBOUND,
            status = MessageStatus.DELIVERED,
            timestampMillis = 2000L
        )
        val msg3 = StoredMessage(
            id = "msg-3",
            conversationId = conversationId,
            senderId = "alice",
            recipientId = "bob",
            plaintext = "Third message",
            direction = MessageDirection.OUTBOUND,
            status = MessageStatus.PENDING_OUTBOX,
            timestampMillis = 3000L
        )

        // Save in non-chronological order to test ordering logic
        messageRepo.saveMessage(msg3)
        messageRepo.saveMessage(msg1)
        messageRepo.saveMessage(msg2)

        // All messages should be returned in chronological order (oldest to newest)
        val allMessages = messageRepo.getMessages(conversationId)
        assertEquals(3, allMessages.size)
        assertEquals("msg-1", allMessages[0].id)
        assertEquals("msg-2", allMessages[1].id)
        assertEquals("msg-3", allMessages[2].id)

        // Test limit (should retrieve the latest 2 messages in chronological order)
        val limited = messageRepo.getMessages(conversationId, limit = 2)
        assertEquals(2, limited.size)
        assertEquals("msg-2", limited[0].id)
        assertEquals("msg-3", limited[1].id)

        // Test pagination beforeTimestamp (messages before 3000L)
        val paginated = messageRepo.getMessages(conversationId, limit = 10, beforeTimestamp = 3000L)
        assertEquals(2, paginated.size)
        assertEquals("msg-1", paginated[0].id)
        assertEquals("msg-2", paginated[1].id)
    }

    @Test
    fun testObserveConversationUpdates() = runTest {
        val conversationId = "conv-reactive"
        val flow = messageRepo.observeConversation(conversationId)
        assertEquals(0, flow.value.size)

        val msg1 = StoredMessage(
            id = "reactive-1",
            conversationId = conversationId,
            senderId = "user1",
            recipientId = "user2",
            plaintext = "Hello reactive world",
            direction = MessageDirection.OUTBOUND,
            status = MessageStatus.PENDING_OUTBOX,
            timestampMillis = 5000L
        )

        messageRepo.saveMessage(msg1)
        assertEquals(1, flow.value.size)
        assertEquals("reactive-1", flow.value[0].id)
        assertEquals(MessageStatus.PENDING_OUTBOX, flow.value[0].status)

        // Update status and observe change
        messageRepo.updateStatus("reactive-1", MessageStatus.SENT_RELAY, relayEnvelopeId = "env-999")
        assertEquals(1, flow.value.size)
        assertEquals(MessageStatus.SENT_RELAY, flow.value[0].status)
        assertEquals("env-999", flow.value[0].relayEnvelopeId)

        // Add second message
        val msg2 = StoredMessage(
            id = "reactive-2",
            conversationId = conversationId,
            senderId = "user2",
            recipientId = "user1",
            plaintext = "Echo reactive world",
            direction = MessageDirection.INBOUND,
            status = MessageStatus.DELIVERED,
            timestampMillis = 6000L
        )

        messageRepo.saveMessage(msg2)
        assertEquals(2, flow.value.size)
        assertEquals("reactive-1", flow.value[0].id)
        assertEquals("reactive-2", flow.value[1].id)
    }

    @Test
    fun testOutboxQueueOperationsStatusChange() = runTest {
        assertEquals(0, outboxQueue.getPendingCount())

        val item = OutboxItem(
            id = "outbox-1",
            conversationId = "conv-outbox",
            recipientMailboxToken = "mailbox-token-123",
            recipientX25519PublicKeyHex = "aabbccddeeff",
            payloadPlaintext = "Secure message payload",
            humanOnly = true
        )

        outboxQueue.enqueue(item)
        assertEquals(1, outboxQueue.getPendingCount())

        val pendingList = outboxQueue.peekPending()
        assertEquals(1, pendingList.size)
        assertEquals("outbox-1", pendingList[0].id)
        assertEquals(MessageStatus.PENDING_OUTBOX, pendingList[0].status)
        assertTrue(pendingList[0].humanOnly)

        // Mark in flight
        outboxQueue.markInFlight(item.id)
        assertEquals(0, outboxQueue.getPendingCount())
        assertEquals(0, outboxQueue.peekPending().size)

        val inFlightItem = outboxQueue.getItem(item.id)
        assertNotNull(inFlightItem)
        assertEquals(MessageStatus.SENDING, inFlightItem.status)
        assertEquals(1, inFlightItem.attempts)
        assertTrue(inFlightItem.lastAttemptMillis > 0L)

        // Mark completed (status change)
        outboxQueue.markCompleted(item.id)
        assertEquals(0, outboxQueue.getPendingCount())

        val completedItem = outboxQueue.getItem(item.id)
        assertNotNull(completedItem)
        assertEquals(MessageStatus.DELIVERED, completedItem.status)
    }

    @Test
    fun testOutboxQueueOperationsRemoval() = runTest {
        val removalStorage = SqliteStorage.inMemory(removeOnComplete = true)
        try {
            val queue = removalStorage.outboxQueue
            val item = OutboxItem(
                id = "outbox-remove-1",
                conversationId = "conv-remove",
                recipientMailboxToken = "token-abc",
                recipientX25519PublicKeyHex = "11223344",
                payloadPlaintext = "Will be removed on complete"
            )

            queue.enqueue(item)
            assertEquals(1, queue.getPendingCount())

            queue.markInFlight(item.id)
            queue.markCompleted(item.id)

            assertEquals(0, queue.getPendingCount())
            assertNull(queue.getItem(item.id))
        } finally {
            removalStorage.close()
        }
    }

    @Test
    fun testOutboxQueueFailure() = runTest {
        val item = OutboxItem(
            id = "outbox-fail-1",
            conversationId = "conv-fail",
            recipientMailboxToken = "token-fail",
            recipientX25519PublicKeyHex = "55667788",
            payloadPlaintext = "May fail"
        )

        outboxQueue.enqueue(item)
        outboxQueue.markInFlight(item.id)
        outboxQueue.markFailed(item.id, "Connection refused")

        assertEquals(0, outboxQueue.getPendingCount())
        val failed = outboxQueue.getItem(item.id)
        assertNotNull(failed)
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals("Connection refused", failed.lastError)
    }

    @Test
    fun testUpdateMessageStatusTransitions() = runTest {
        val msg = StoredMessage(
            id = "msg-transition",
            conversationId = "conv-trans",
            senderId = "self",
            recipientId = "peer",
            plaintext = "State transition test",
            direction = MessageDirection.OUTBOUND,
            status = MessageStatus.PENDING_OUTBOX,
            timestampMillis = 10000L
        )

        messageRepo.saveMessage(msg)
        var stored = messageRepo.getMessageById("msg-transition")
        assertNotNull(stored)
        assertEquals(MessageStatus.PENDING_OUTBOX, stored.status)
        assertNull(stored.relayEnvelopeId)

        // Transition: PENDING_OUTBOX -> SENT_RELAY
        messageRepo.updateStatus("msg-transition", MessageStatus.SENT_RELAY, relayEnvelopeId = "relay-env-001")
        stored = messageRepo.getMessageById("msg-transition")
        assertNotNull(stored)
        assertEquals(MessageStatus.SENT_RELAY, stored.status)
        assertEquals("relay-env-001", stored.relayEnvelopeId)

        // Transition: SENT_RELAY -> DELIVERED (relay envelope ID preserved)
        messageRepo.updateStatus("msg-transition", MessageStatus.DELIVERED)
        stored = messageRepo.getMessageById("msg-transition")
        assertNotNull(stored)
        assertEquals(MessageStatus.DELIVERED, stored.status)
        assertEquals("relay-env-001", stored.relayEnvelopeId)
    }

    @Test
    fun testGetMessageById() = runTest {
        val msg = StoredMessage(
            id = "unique-msg-id",
            conversationId = "conv-id",
            senderId = "alice",
            recipientId = "bob",
            plaintext = "Lookup test",
            direction = MessageDirection.INBOUND,
            status = MessageStatus.DELIVERED,
            timestampMillis = 777L,
            annotationsJson = "{\"topics\":[\"test\"]}"
        )

        messageRepo.saveMessage(msg)
        val fetched = messageRepo.getMessageById("unique-msg-id")
        assertNotNull(fetched)
        assertEquals(msg.id, fetched.id)
        assertEquals(msg.plaintext, fetched.plaintext)
        assertEquals(msg.annotationsJson, fetched.annotationsJson)

        val notFound = messageRepo.getMessageById("non-existent-id")
        assertNull(notFound)
    }
}
