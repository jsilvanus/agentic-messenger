package com.agenticmessenger.ui.molecular

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.generative.GenerativeUiRegistry
import com.agenticmessenger.ui.model.ConversationViewMode
import com.agenticmessenger.ui.model.MeshProvenanceType
import com.agenticmessenger.ui.model.TimelineItemUiModel
import com.agenticmessenger.ui.presenter.ConversationPresenter
import kotlin.math.roundToInt

/**
 * Organism Document Component: DocumentChatFlow
 *
 * AUTHORITATIVE OWNERSHIP: Dictator Ecosystem (D46 / TCR Document Model)
 * - Dictator owns all voice-first document editing interfaces, caret-anchored actor pills (Ember),
 *   and continuous transcript composition flows.
 * - In AMP, the conversation is rendered as a continuous living document.
 * - Edits and speech flow directly into the active document tail at the bottom,
 *   visually differentiated with a subtle container tint and horizontal demarcation line.
 * - The Red Pill (Ember cursor, 0xFFEF4444) sits at the active text caret at rest.
 * - The user can drag the Red Pill:
 *     - Dragging onto a previous message attaches it as context for voice / orchestrator queries.
 *     - Dragging back to the editable tail restores the active typing/dictation caret.
 * - Gestures:
 *     - Tap: quick voice / orchestrator prompt at cursor.
 *     - Double-tap or long-press: Dictator-style Transcript Split Drawer.
 */
@Composable
fun DocumentChatFlow(
    items: List<TimelineItemUiModel>,
    currentUserId: String,
    inputDraft: String,
    onInputDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    viewMode: ConversationViewMode,
    listState: LazyListState,
    presenter: ConversationPresenter,
    modifier: Modifier = Modifier,
    onOrchestratorCommand: (command: String, targetMessageId: String?) -> Unit = { _, _ -> }
) {
    // Caret / Drag state
    var attachedMessageId by remember { mutableStateOf<String?>(null) }
    var attachedMessageSnippet by remember { mutableStateOf<String?>(null) }

    // Floating Red Pill drag offset from caret rest position
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    // Drawer state
    var isTranscriptDrawerOpen by remember { mutableStateOf(false) }
    var isQuickPromptOpen by remember { mutableStateOf(false) }
    var quickPromptText by remember { mutableStateOf("") }

    // Pulsing pip animation for Ember cursor
    val infiniteTransition = rememberInfiniteTransition()
    val pipPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Continuous Living Document (Chat Stream)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val isAttached = attachedMessageId == item.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isAttached) {
                                    Modifier.border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                // Clicking an item attaches context if dragging was not used
                                if (attachedMessageId == item.id) {
                                    attachedMessageId = null
                                    attachedMessageSnippet = null
                                } else {
                                    attachedMessageId = item.id
                                    attachedMessageSnippet = when (item) {
                                        is TimelineItemUiModel.Message -> item.naturalText.take(40)
                                        else -> "Item: ${item.id}"
                                    }
                                }
                            }
                    ) {
                        GenerativeUiRegistry.RenderItem(
                            item = item,
                            currentUserId = currentUserId,
                            onApproveDisclosure = { presenter.approveDisclosure(it) },
                            onDeclineDisclosure = { presenter.declineDisclosure(it) },
                            onConfirmCalendar = { presenter.confirmCalendarAction(item.id) },
                            onToggleStt = { presenter.toggleSttVisibility(it) },
                            onUnshieldForAi = { presenter.unshieldMessageForAi(it) }
                        )
                    }
                }
            }

            // 2. Active Document Tail (Input Section) — Dictator Document Component
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Demarcation line
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        thickness = 1.5.dp
                    )

                    // Context banner when Red Pill is attached to a prior message block
                    if (!attachedMessageSnippet.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .background(
                                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🎯 Attached to Context:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "\"$attachedMessageSnippet…\"",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "✕ Detach",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier
                                    .clickable {
                                        attachedMessageId = null
                                        attachedMessageSnippet = null
                                    }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }

                    // Editable document tail: text flows from cursor into this region
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            if (inputDraft.isEmpty()) {
                                Text(
                                    text = "Dictate or type into document flow...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            BasicTextField(
                                value = inputDraft,
                                onValueChange = onInputDraftChanged,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(Color(0xFFEF4444)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Send button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputDraft.isNotBlank()) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                                .clickable(enabled = inputDraft.isNotBlank()) { onSend() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↑",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Floating, Draggable Red Pill (Ember Caret Cursor from Dictator)
        // Positioned at the bottom active input region by default, freely draggable across messages
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 58.dp, end = 52.dp)
                .offset {
                    IntOffset(
                        dragOffset.x.roundToInt(),
                        dragOffset.y.roundToInt()
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            isQuickPromptOpen = !isQuickPromptOpen
                        },
                        onDoubleTap = {
                            isTranscriptDrawerOpen = !isTranscriptDrawerOpen
                        },
                        onLongPress = {
                            isTranscriptDrawerOpen = true
                        }
                    )
                }
        ) {
            // Ember pill capsule with glowing pip
            Row(
                modifier = Modifier
                    .shadow(if (isDragging) 8.dp else 4.dp, RoundedCornerShape(14.dp))
                    .background(Color(0xFFEF4444), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .scale(if (isDragging) 1.12f else 1.0f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Pulsing luminous beacon pip
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = pipPulse))
                )
                Text(
                    text = if (isDragging) "Dragging Cursor" else "Ember",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 4. Quick Orchestrator / Voice Prompt Popover (on Single Tap of Red Pill)
        if (isQuickPromptOpen) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 70.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔴 Orchestrator at Cursor",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                        Text(
                            text = "✕",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { isQuickPromptOpen = false }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = quickPromptText,
                            onValueChange = { quickPromptText = it },
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEF4444))
                                .clickable {
                                    if (quickPromptText.isNotBlank()) {
                                        onOrchestratorCommand(quickPromptText, attachedMessageId)
                                        quickPromptText = ""
                                        isQuickPromptOpen = false
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Run", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Hint: 'Tell Bob I am ready' (default: Agent) or 'Send to Bob as me: Hello' (Ghostwrite)",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 5. Dictator-Style Transcript Split Drawer (on Double-Click or Hold of Red Pill)
        AnimatedVisibility(
            visible = isTranscriptDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 64.dp)
                    .shadow(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Transcript & Orchestrator Audit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dictator Caret Actor Log (D46)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { isTranscriptDrawerOpen = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Close", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Active Mesh Routing:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Default Dispatch: 🤖 AGENT_DELEGATE (Delegated Runner)\n• Ghostwrite Mode: 👤 HUMAN_DIRECT (Send as Me)\n• Privacy Ceiling: Sealed messages disallowed from AI tool loops",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Recent Turns in Document Flow:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items.takeLast(10)) { item ->
                            when (item) {
                                is TimelineItemUiModel.Message -> {
                                    val badge = when (item.provenanceType) {
                                        MeshProvenanceType.AGENT_RUNNER -> "🤖 Agent"
                                        MeshProvenanceType.HUMAN_DIRECT -> "👤 Human"
                                        MeshProvenanceType.HUMAN_ONLY_E2EE -> "🔒 Sealed"
                                        MeshProvenanceType.SYSTEM_SERVICE -> "⚙️ System"
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(item.sender.principalId, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text(badge, fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(item.naturalText, fontSize = 11.sp)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
