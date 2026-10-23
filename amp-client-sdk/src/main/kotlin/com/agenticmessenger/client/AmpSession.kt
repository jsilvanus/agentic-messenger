package com.agenticmessenger.client

import com.agenticmessenger.client.identity.ClientIdentity
import com.agenticmessenger.client.model.MessageDirection
import com.agenticmessenger.client.model.MessageStatus
import com.agenticmessenger.client.model.OutboxItem
import com.agenticmessenger.client.model.StoredMessage
import com.agenticmessenger.client.network.AmpConnectionState
import com.agenticmessenger.client.network.AmpRelayClient
import com.agenticmessenger.client.storage.MessageRepository
import com.agenticmessenger.client.storage.OutboxQueue
import com.agenticmessenger.client.storage.SqliteStorage
import com.agenticmessenger.core.model.PrivacyLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.UUID

/**
 * High-level AMP Client Session orchestrating:
 * - End-to-end encrypted relay communication via AmpRelayClient.
 * - Local offline Outbox staging and automatic replay.
 * - Reactive conversation history powered by SQLite MessageRepository.
 */
class AmpSession(
    val identity: ClientIdentity,
    val storage: SqliteStorage,
    val relayWsUrl: String,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val relayClient: AmpRelayClient = AmpRelayClient(serverWsUrl = relayWsUrl, identity = identity, scope = scope)
) : Closeable {

    private val logger = LoggerFactory.getLogger(AmpSession::class.java)

    val messageRepo: MessageRepository = storage.messageRepository
    val outboxQueue: OutboxQueue = storage.outboxQueue

    val connectionState: StateFlow<AmpConnectionState> = relayClient.connectionState

    private var outboxWorkerJob: Job? = null
    private var inboundListenerJob: Job? = null

    /**
     * Starts the session: connects to the blind relay, starts the inbound listener,
     * and sets up the outbox drain worker.
     */
    fun start() {
        relayClient.connect()

        // 1. Inbound listener
        inboundListenerJob = scope.launch {
            relayClient.incomingMessages.collect { incoming ->
                val stored = StoredMessage(
                    id = incoming.messageId,
                    conversationId = incoming.conversationId,
                    senderId = incoming.senderPrincipalId,
                    recipientId = identity.principalId,
                    plaintext = incoming.plaintext,
                    direction = MessageDirection.INBOUND,
                    status = MessageStatus.DELIVERED,
                    timestampMillis = incoming.timestamp,
                    relayEnvelopeId = incoming.messageId
                )
                messageRepo.saveMessage(stored)
                logger.debug("Received and persisted message '{}' for conversation '{}'", incoming.messageId, incoming.conversationId)
            }
        }

        // 2. Outbox drain worker watching connection state
        outboxWorkerJob = scope.launch {
            relayClient.connectionState.collect { state ->
                if (state == AmpConnectionState.CONNECTED) {
                    drainOutbox()
                }
            }
        }
    }

    /**
     * Observes real-time message stream for a given conversation.
     */
    fun observeConversation(conversationId: String): StateFlow<List<StoredMessage>> {
        return messageRepo.observeConversation(conversationId)
    }

    /**
     * Asynchronously sends a direct message:
     * 1. Saves to local message history as OUTBOUND with PENDING_OUTBOX status.
     * 2. Enqueues in local OutboxQueue for durability.
     * 3. If online, immediately dispatches to the relay and marks SENT_RELAY.
     */
    suspend fun sendMessage(
        recipientMailboxToken: String,
        recipientPrincipalId: String,
        recipientX25519PublicKeyHex: String,
        plaintext: String,
        policy: PrivacyLevel = PrivacyLevel.HUMAN_ONLY,
        conversationId: String = recipientPrincipalId
    ): StoredMessage {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val stored = StoredMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = identity.principalId,
            recipientId = recipientPrincipalId,
            plaintext = plaintext,
            direction = MessageDirection.OUTBOUND,
            status = MessageStatus.PENDING_OUTBOX,
            timestampMillis = now
        )

        val outboxItem = OutboxItem(
            id = messageId,
            conversationId = conversationId,
            recipientMailboxToken = recipientMailboxToken,
            recipientX25519PublicKeyHex = recipientX25519PublicKeyHex,
            payloadPlaintext = plaintext,
            humanOnly = (policy == PrivacyLevel.HUMAN_ONLY),
            status = MessageStatus.PENDING_OUTBOX,
            createdAtMillis = now
        )

        // Stage in local DB
        messageRepo.saveMessage(stored)
        outboxQueue.enqueue(outboxItem)

        // Attempt immediate dispatch if connected
        if (connectionState.value == AmpConnectionState.CONNECTED) {
            dispatchOutboxItem(outboxItem)
        }

        return stored
    }

    /**
     * Drains pending items from the outbox queue when connected.
     */
    suspend fun drainOutbox() {
        val pending = outboxQueue.peekPending(50)
        for (item in pending) {
            dispatchOutboxItem(item)
        }
    }

    private suspend fun dispatchOutboxItem(item: OutboxItem) {
        try {
            outboxQueue.markInFlight(item.id)
            val policy = if (item.humanOnly) PrivacyLevel.HUMAN_ONLY else PrivacyLevel.AGENT_ACCESSIBLE
            val relayMsgId = relayClient.sendDirectMessage(
                recipientMailboxToken = item.recipientMailboxToken,
                recipientPrincipalId = item.conversationId,
                recipientX25519PublicKeyHex = item.recipientX25519PublicKeyHex,
                plaintext = item.payloadPlaintext,
                policy = policy,
                conversationId = item.conversationId,
                messageId = item.id
            )
            outboxQueue.markCompleted(item.id)
            messageRepo.updateStatus(item.id, MessageStatus.SENT_RELAY, relayMsgId)
            logger.debug("Successfully dispatched outbox message '{}'", item.id)
        } catch (e: Exception) {
            logger.warn("Failed to dispatch outbox message '{}': {}", item.id, e.message)
            outboxQueue.markFailed(item.id, e.message ?: "Dispatch failure")
        }
    }

    override fun close() {
        outboxWorkerJob?.cancel()
        inboundListenerJob?.cancel()
        relayClient.close()
        storage.close()
    }
}
