package com.agenticmessenger.desktop.bridge

import com.agenticmessenger.desktop.model.ConnectionStatus
import com.agenticmessenger.desktop.model.DesktopChatMessage
import com.agenticmessenger.desktop.model.DesktopIdentity
import com.agenticmessenger.ui.model.MeshProvenanceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Transport bridge interface for Desktop messenger instances.
 *
 * Provides the hook for when the client SDK is wired in, while offering
 * an instant in-memory loopback router for dual-window local testing.
 */
interface DesktopTransportBridge {
    fun register(mailboxId: String, onMessageReceived: (DesktopChatMessage) -> Unit)
    fun unregister(mailboxId: String)
    fun dispatchMessage(
        from: DesktopIdentity,
        toMailboxId: String,
        text: String,
        isHumanOnly: Boolean = false
    ): DesktopChatMessage
    fun getConnectionStatusFlow(mailboxId: String): StateFlow<ConnectionStatus>
    fun setConnectionStatus(mailboxId: String, status: ConnectionStatus)
}

/**
 * In-memory loopback transport router.
 * Routes messages locally between spawned desktop windows in the same JVM process.
 * Can be swapped with the real WebSocket Client SDK when complete.
 */
class InMemoryDesktopLoopbackBridge : DesktopTransportBridge {

    private val receivers = ConcurrentHashMap<String, (DesktopChatMessage) -> Unit>()
    private val statuses = ConcurrentHashMap<String, MutableStateFlow<ConnectionStatus>>()

    override fun register(mailboxId: String, onMessageReceived: (DesktopChatMessage) -> Unit) {
        receivers[mailboxId] = onMessageReceived
        getOrCreateStatusFlow(mailboxId).value = ConnectionStatus.CONNECTED
    }

    override fun unregister(mailboxId: String) {
        receivers.remove(mailboxId)
        getOrCreateStatusFlow(mailboxId).value = ConnectionStatus.OFFLINE
    }

    override fun dispatchMessage(
        from: DesktopIdentity,
        toMailboxId: String,
        text: String,
        isHumanOnly: Boolean
    ): DesktopChatMessage {
        val now = System.currentTimeMillis()
        val msgId = "msg_${now}_${(100..999).random()}"

        // Check if recipient is connected
        val recipientOnline = receivers.containsKey(toMailboxId) &&
                getOrCreateStatusFlow(toMailboxId).value == ConnectionStatus.CONNECTED

        // Local message for sender
        val senderMessage = DesktopChatMessage(
            id = msgId,
            senderIdentityId = from.id,
            senderDisplayName = from.displayName,
            text = text,
            timestamp = now,
            isSentByMe = true,
            isDelivered = recipientOnline,
            provenanceType = if (isHumanOnly) MeshProvenanceType.HUMAN_ONLY_E2EE else MeshProvenanceType.HUMAN_DIRECT,
            isHumanOnly = isHumanOnly
        )

        // If recipient is registered and online, deliver incoming copy
        val recipientCallback = receivers[toMailboxId]
        if (recipientCallback != null && recipientOnline) {
            val incomingMessage = DesktopChatMessage(
                id = msgId,
                senderIdentityId = from.id,
                senderDisplayName = from.displayName,
                text = text,
                timestamp = now,
                isSentByMe = false,
                isDelivered = true,
                provenanceType = if (isHumanOnly) MeshProvenanceType.HUMAN_ONLY_E2EE else MeshProvenanceType.HUMAN_DIRECT,
                isHumanOnly = isHumanOnly
            )
            recipientCallback(incomingMessage)
        }

        return senderMessage
    }

    override fun getConnectionStatusFlow(mailboxId: String): StateFlow<ConnectionStatus> {
        return getOrCreateStatusFlow(mailboxId).asStateFlow()
    }

    override fun setConnectionStatus(mailboxId: String, status: ConnectionStatus) {
        getOrCreateStatusFlow(mailboxId).value = status
    }

    private fun getOrCreateStatusFlow(mailboxId: String): MutableStateFlow<ConnectionStatus> {
        return statuses.computeIfAbsent(mailboxId) { MutableStateFlow(ConnectionStatus.CONNECTED) }
    }

    companion object {
        val shared = InMemoryDesktopLoopbackBridge()
    }
}
