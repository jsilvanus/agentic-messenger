package com.agenticmessenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.atomic.PresenceIndicator
import com.agenticmessenger.ui.model.PresenceState
import com.agenticmessenger.ui.molecular.DocumentChatFlow
import com.agenticmessenger.ui.molecular.DualViewToggle
import com.agenticmessenger.ui.presenter.ConversationPresenter

/**
 * Screen: ConversationScreen (Compose Multiplatform)
 *
 * Implements the continuous Living Document chat flow adopting Dictator's TCR Document Model:
 * - Contact + Aidos presence header
 * - Continuous Document Flow with history items
 * - Active Document Input Section at the tail (differentiated with horizontal divider and soft tint)
 * - Draggable Red Pill (Ember cursor from Dictator) anchored at caret, draggable to messages for context
 * - Single click: quick orchestrator prompt
 * - Double click / long-press: Dictator-style Transcript Split Drawer
 */
@Composable
fun ConversationScreen(
    presenter: ConversationPresenter,
    currentUserId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAskAiTrigger: (() -> Unit)? = { presenter.askAi() }
) {
    val state by presenter.state.collectAsState()
    val contact = state.contact
    val listState = rememberLazyListState()

    // Auto-scroll to latest item on new message
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back arrow
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Contact Avatar & Presence Info
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = contact?.displayName ?: "Mesh Peer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            PresenceIndicator(
                                presence = contact?.presence ?: PresenceState.ONLINE,
                                isAidosActive = contact?.isAidosActive ?: true,
                                showLabel = true
                            )
                        }
                        Text(
                            text = contact?.handle ?: "did:aidos:peer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Dual-View Toggle
                    DualViewToggle(
                        currentMode = state.viewMode,
                        onModeChanged = { presenter.onViewModeChanged(it) }
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    thickness = 1.dp
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            DocumentChatFlow(
                items = state.items,
                currentUserId = currentUserId,
                inputDraft = state.inputDraft,
                onInputDraftChanged = { presenter.onInputDraftChanged(it) },
                onSend = { presenter.sendMessage() },
                viewMode = state.viewMode,
                listState = listState,
                presenter = presenter,
                onOrchestratorCommand = { cmd, targetId ->
                    val fullPrompt = if (targetId != null) {
                        "Context target message ID: $targetId.\nInstruction: $cmd"
                    } else {
                        cmd
                    }
                    presenter.askAi(fullPrompt)
                }
            )
        }
    }
}
