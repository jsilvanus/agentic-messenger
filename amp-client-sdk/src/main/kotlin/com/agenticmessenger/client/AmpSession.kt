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
import com.agenticmessenger.core.model.AttachmentPayload
import com.agenticmessenger.core.model.EndpointType
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.core.protocol.RelayFrame
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
 * - First-class support for Human and AI Agent participants.
 * - Sealed Voice Notes & Attachment progressive disclosure and receipts.
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
    private var receiptListenerJob: Job? = null

    /**
     * Starts the session: connects to the blind relay, starts the inbound listener,
     * sets up the outbox drain worker, and monitors disclosure receipts.
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
                    relayEnvelopeId = incoming.messageId,
                    annotationsJson = if (incoming.policy == PrivacyLevel.HUMAN_ONLY) """[{"type":"HUMAN_ONLY"}]""" else null
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

        // 3. Raw frames listener for DISCLOSURE_RECEIPT
        receiptListenerJob = scope.launch {
            relayClient.rawFrames.collect { frame ->
                if (frame is RelayFrame.DisclosureReceipt) {
                    handleDisclosureReceipt(frame)
                }
            }
        }
    }

    private suspend fun handleDisclosureReceipt(receipt: RelayFrame.DisclosureReceipt) {
        val existing = messageRepo.getMessageById(receipt.messageId) ?: return
        val currentJson = existing.annotationsJson ?: ""
        val updatedJson = if (currentJson.isBlank()) {
            """[{"type":"disclosure_receipt_received"}]"""
        } else if (!currentJson.contains("disclosure_receipt_received")) {
            currentJson.removeSuffix("]") + """,{"type":"disclosure_receipt_received"}]"""
        } else {
            currentJson
        }
        val updated = existing.copy(annotationsJson = updatedJson)
        messageRepo.saveMessage(updated)
        logger.info("Marked message '{}' as disclosed by recipient for local AI", receipt.messageId)
    }

    /**
     * Recipient unshields a received sealed message for their local AI assistant.
     * Updates local storage annotations, and transmits an informational DISCLOSURE_RECEIPT
     * to the original sender's mailbox. The sender's copy itself is NOT unshielded.
     */
    suspend fun unshieldMessageForAi(messageId: String, senderMailboxToken: String? = null) {
        val existing = messageRepo.getMessageById(messageId) ?: return
        val currentJson = existing.annotationsJson ?: ""
        val updatedJson = if (currentJson.isBlank()) {
            """[{"type":"unshielded_for_ai"}]"""
        } else if (!currentJson.contains("unshielded_for_ai")) {
            currentJson.removeSuffix("]") + """,{"type":"unshielded_for_ai"}]"""
        } else {
            currentJson
        }
        val updated = existing.copy(annotationsJson = updatedJson)
        messageRepo.saveMessage(updated)
        logger.info("Unshielded message '{}' for local AI assistant", messageId)

        val targetMailbox = senderMailboxToken ?: existing.conversationId
        val receiptFrame = RelayFrame.DisclosureReceipt(
            messageId = messageId,
            sourceMailbox = identity.mailboxToken,
            destinationMailbox = targetMailbox,
            notice = "Unshielded by recipient for local AI assistant"
        )
        if (connectionState.value == AmpConnectionState.CONNECTED) {
            try {
                relayClient.sendFrame(receiptFrame)
                logger.debug("Sent DisclosureReceipt frame for message '{}' to '{}'", messageId, targetMailbox)
            } catch (e: Exception) {
                logger.warn("Failed to dispatch disclosure receipt frame: {}", e.message)
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
     * Asynchronously sends a direct message (human or agent):
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
        conversationId: String = recipientPrincipalId,
        recipientEndpointType: EndpointType = EndpointType.HUMAN_DIRECT,
        recipientAgentX25519PublicKeyHex: String? = null,
        annotationsJson: String? = null,
        attachments: List<AttachmentPayload> = emptyList(),
        isShieldedVoice: Boolean = false,
        shieldedSttTranscript: String? = null
    ): StoredMessage {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val effectiveAnnotations = buildAnnotationsJson(
            baseJson = annotationsJson,
            policy = policy,
            isShieldedVoice = isShieldedVoice,
            shieldedSttTranscript = shieldedSttTranscript,
            attachmentsCount = attachments.size
        )

        val stored = StoredMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = identity.principalId,
            recipientId = recipientPrincipalId,
            plaintext = plaintext,
            direction = MessageDirection.OUTBOUND,
            status = MessageStatus.PENDING_OUTBOX,
            timestampMillis = now,
            annotationsJson = effectiveAnnotations
        )

        val outboxItem = OutboxItem(
            id = messageId,
            conversationId = conversationId,
            recipientMailboxToken = recipientMailboxToken,
            recipientX25519PublicKeyHex = recipientX25519PublicKeyHex,
            payloadPlaintext = plaintext,
            humanOnly = (policy == PrivacyLevel.HUMAN_ONLY),
            status = MessageStatus.PENDING_OUTBOX,
            createdAtMillis = now,
            recipientPrincipalId = recipientPrincipalId
        )

        // Stage in local DB
        messageRepo.saveMessage(stored)
        outboxQueue.enqueue(outboxItem)

        // Attempt immediate dispatch if connected
        if (connectionState.value == AmpConnectionState.CONNECTED) {
            dispatchOutboxItem(
                item = outboxItem,
                recipientEndpointType = recipientEndpointType,
                recipientAgentX25519PublicKeyHex = recipientAgentX25519PublicKeyHex
            )
        }

        return stored
    }

    private fun buildAnnotationsJson(
        baseJson: String?,
        policy: PrivacyLevel,
        isShieldedVoice: Boolean,
        shieldedSttTranscript: String?,
        attachmentsCount: Int
    ): String {
        val list = mutableListOf<String>()
        if (policy == PrivacyLevel.HUMAN_ONLY) {
            list.add(""""policy":"HUMAN_ONLY"""")
        }
        if (isShieldedVoice) {
            list.add(""""is_shielded_voice":true""")
        }
        if (!shieldedSttTranscript.isNullOrBlank()) {
            list.add(""""shielded_stt_transcript":${kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer(), shieldedSttTranscript)}""")
        }
        if (attachmentsCount > 0) {
            list.add(""""attachments_count":$attachmentsCount""")
        }
        if (baseJson.isNullOrBlank()) {
            return if (list.isEmpty()) "" else "[{${list.joinToString(",")}}]"
        }
        if (list.isEmpty()) return baseJson
        return baseJson.removeSuffix("]") + ",{${list.joinToString(",")}}]"
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

    private suspend fun dispatchOutboxItem(
        item: OutboxItem,
        recipientEndpointType: EndpointType = EndpointType.HUMAN_DIRECT,
        recipientAgentX25519PublicKeyHex: String? = null
    ) {
        try {
            outboxQueue.markInFlight(item.id)
            val policy = if (item.humanOnly) PrivacyLevel.HUMAN_ONLY else PrivacyLevel.AGENT_ACCESSIBLE
            val relayMsgId = relayClient.sendDirectMessage(
                recipientMailboxToken = item.recipientMailboxToken,
                recipientPrincipalId = item.recipientPrincipalId,
                recipientX25519PublicKeyHex = item.recipientX25519PublicKeyHex,
                plaintext = item.payloadPlaintext,
                policy = policy,
                conversationId = item.conversationId,
                messageId = item.id,
                recipientEndpointType = recipientEndpointType,
                recipientAgentX25519PublicKeyHex = recipientAgentX25519PublicKeyHex
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
        receiptListenerJob?.cancel()
        relayClient.close()
        storage.close()
    }
}
