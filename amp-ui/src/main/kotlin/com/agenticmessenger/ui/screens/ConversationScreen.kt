package com.agenticmessenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.atomic.PresenceIndicator
import com.agenticmessenger.ui.generative.GenerativeUiRegistry
import com.agenticmessenger.ui.model.ConversationViewMode
import com.agenticmessenger.ui.model.PresenceState
import com.agenticmessenger.ui.model.TimelineItemUiModel
import com.agenticmessenger.ui.molecular.ConversationInputBar
import com.agenticmessenger.ui.molecular.CueTickerRow
import com.agenticmessenger.ui.molecular.DualViewToggle
import com.agenticmessenger.ui.presenter.ConversationPresenter

/**
 * Screen: ConversationScreen (Compose Multiplatform)
 *
 * Implements the 4-party collaborative conversation mesh:
 * - Contact + Aidos presence header
 * - Dual-View toggle (Natural Language Stream vs Condensed Cue-Words Ticker)
 * - Timeline with:
 *   - Human Direct turns
 *   - Delegated Runner / Aidos Assistant turns
 *   - Human-Only hardware-enclave sealed turns
 *   - 1-tap Progressive Disclosure Approval cards
 *   - Inline Calendar Co-Pilot cards
 *   - Voice dictation waveform playback
 * - Composer with Human-Only privacy lock switch
 */
@Composable
fun ConversationScreen(
    presenter: ConversationPresenter,
    currentUserId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onVoiceDictationTrigger: () -> Unit = {},
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
        },
        bottomBar = {
            ConversationInputBar(
                text = state.inputDraft,
                onTextChanged = { presenter.onInputDraftChanged(it) },
                isHumanOnly = state.isHumanOnlyE2EEMode,
                onToggleHumanOnly = { presenter.toggleHumanOnlyMode() },
                onVoiceDictationTrigger = onVoiceDictationTrigger,
                onAskAiTrigger = onAskAiTrigger,
                onSend = { presenter.sendMessage() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Encrypted 4-Party Conversation Mesh Initialized.\nSay hello or ask Aidos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (state.viewMode) {
                        ConversationViewMode.NATURAL_STREAM -> {
                            items(state.items, key = { it.id }) { item ->
                                GenerativeUiRegistry.RenderItem(
                                    item = item,
                                    currentUserId = currentUserId,
                                    onApproveDisclosure = { presenter.approveDisclosure(it) },
                                    onDeclineDisclosure = { presenter.declineDisclosure(it) },
                                    onConfirmCalendar = { presenter.confirmCalendarAction(item.id) }
                                )
                            }
                        }
                        ConversationViewMode.CUE_WORDS_TICKER -> {
                            items(state.items, key = { it.id }) { item ->
                                when (item) {
                                    is TimelineItemUiModel.Message -> {
                                        CueTickerRow(
                                            timestamp = item.timestamp,
                                            senderName = item.sender.principalId.substringAfterLast(":"),
                                            provenanceType = item.provenanceType,
                                            cueWords = item.cueWords
                                        )
                                    }
                                    is TimelineItemUiModel.ProgressiveDisclosureRequest -> {
                                        CueTickerRow(
                                            timestamp = item.timestamp,
                                            senderName = item.requester.principalId.substringAfterLast(":"),
                                            provenanceType = com.agenticmessenger.ui.model.MeshProvenanceType.AGENT_RUNNER,
                                            cueWords = listOf("Disclosure Request", item.topicKey, item.status.name)
                                        )
                                    }
                                    is TimelineItemUiModel.CalendarProposal -> {
                                        CueTickerRow(
                                            timestamp = item.timestamp,
                                            senderName = "Aidos",
                                            provenanceType = com.agenticmessenger.ui.model.MeshProvenanceType.AGENT_RUNNER,
                                            cueWords = listOf("Calendar", item.proposedTime, if (item.isActionTaken) "Confirmed" else "Pending")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
