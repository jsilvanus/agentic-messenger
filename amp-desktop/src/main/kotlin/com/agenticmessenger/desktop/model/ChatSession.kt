package com.agenticmessenger.desktop.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.agenticmessenger.desktop.bridge.DesktopTransportBridge

/**
 * Live session state for a spawned chat window instance.
 */
class ChatSession(
    val id: String,
    val identity: DesktopIdentity,
    var targetPeer: DesktopIdentity,
    initialX: Int = 100,
    initialY: Int = 100,
    private val bridge: DesktopTransportBridge
) {
    val windowState = WindowState(
        position = WindowPosition(initialX.dp, initialY.dp),
        size = DpSize(480.dp, 640.dp)
    )

    val messages = mutableStateListOf<DesktopChatMessage>()
    var inputDraft by mutableStateOf("")
    var isHumanOnly by mutableStateOf(false)
    var connectionStatus by mutableStateOf(ConnectionStatus.CONNECTED)

    init {
        // Register receiver for this session's mailbox
        bridge.register(identity.mailboxId) { incoming ->
            messages.add(incoming)
        }
    }

    fun sendMessage() {
        val trimmed = inputDraft.trim()
        if (trimmed.isEmpty()) return

        val sentMsg = bridge.dispatchMessage(
            from = identity,
            toMailboxId = targetPeer.mailboxId,
            text = trimmed,
            isHumanOnly = isHumanOnly
        )
        messages.add(sentMsg)
        inputDraft = ""
    }

    fun toggleConnection() {
        val newStatus = if (connectionStatus == ConnectionStatus.CONNECTED) {
            ConnectionStatus.OFFLINE
        } else {
            ConnectionStatus.CONNECTED
        }
        connectionStatus = newStatus
        bridge.setConnectionStatus(identity.mailboxId, newStatus)
    }

    fun toggleHumanOnly() {
        isHumanOnly = !isHumanOnly
    }

    fun close() {
        bridge.unregister(identity.mailboxId)
    }
}
