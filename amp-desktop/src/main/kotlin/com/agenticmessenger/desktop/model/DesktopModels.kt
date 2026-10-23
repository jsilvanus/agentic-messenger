package com.agenticmessenger.desktop.model

import androidx.compose.ui.window.WindowState
import com.agenticmessenger.core.crypto.BlindedMailbox
import com.agenticmessenger.ui.model.MeshProvenanceType
import kotlinx.serialization.Serializable

/**
 * Identity profile for a desktop messenger instance.
 */
@Serializable
data class DesktopIdentity(
    val id: String,                 // e.g. "alice"
    val displayName: String,        // e.g. "Alice"
    val handle: String,             // e.g. "did:aidos:alice"
    val deviceKeyId: String,        // e.g. "key_alice_desktop"
    val epochKeyHex: String = "0123456789abcdef0123456789abcdef",
    val mailboxId: String = ""
) {
    companion object {
        fun create(id: String, displayName: String, handle: String, deviceKeyId: String): DesktopIdentity {
            val epoch = "0123456789abcdef0123456789abcdef"
            val mailbox = try {
                BlindedMailbox.compute(epoch, deviceKeyId)
            } catch (_: Exception) {
                "mb_${id}_${deviceKeyId.takeLast(4)}"
            }
            return DesktopIdentity(
                id = id,
                displayName = displayName,
                handle = handle,
                deviceKeyId = deviceKeyId,
                epochKeyHex = epoch,
                mailboxId = mailbox
            )
        }

        val ALICE = create(
            id = "alice",
            displayName = "Alice",
            handle = "did:aidos:alice",
            deviceKeyId = "key_alice_desk_01"
        )

        val BOB = create(
            id = "bob",
            displayName = "Bob",
            handle = "did:aidos:bob",
            deviceKeyId = "key_bob_desk_01"
        )
    }
}

/**
 * Chat message model for Desktop UI timeline.
 */
data class DesktopChatMessage(
    val id: String,
    val senderIdentityId: String,
    val senderDisplayName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSentByMe: Boolean,
    val isDelivered: Boolean = true,
    val provenanceType: MeshProvenanceType = MeshProvenanceType.HUMAN_DIRECT,
    val isHumanOnly: Boolean = false
)

/**
 * Connection status of the desktop instance.
 */
enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    OFFLINE
}
