package com.agenticmessenger.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agenticmessenger.desktop.model.ChatSession
import com.agenticmessenger.desktop.ui.components.ChatBubbleFeed
import com.agenticmessenger.desktop.ui.components.ChatHeader
import com.agenticmessenger.desktop.ui.components.ChatInputBar
import com.agenticmessenger.ui.theme.AmpTheme

/**
 * Full Chat Window content composable for a Desktop chat instance.
 */
@Composable
fun ChatWindowView(session: ChatSession) {
    AmpTheme {
        Scaffold(
            topBar = {
                ChatHeader(
                    identity = session.identity,
                    targetPeer = session.targetPeer,
                    connectionStatus = session.connectionStatus,
                    onToggleConnection = { session.toggleConnection() }
                )
            },
            bottomBar = {
                ChatInputBar(
                    text = session.inputDraft,
                    onTextChanged = { session.inputDraft = it },
                    isHumanOnly = session.isHumanOnly,
                    onToggleHumanOnly = { session.toggleHumanOnly() },
                    onSend = { session.sendMessage() }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize()) {
                ChatBubbleFeed(
                    messages = session.messages,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
