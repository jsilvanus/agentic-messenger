package com.agenticmessenger.ui.molecular

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.core.model.PrivacyLevel
import com.agenticmessenger.ui.atomic.CueChip
import com.agenticmessenger.ui.atomic.PrivacySealBadge
import com.agenticmessenger.ui.atomic.ProvenanceBadge
import com.agenticmessenger.ui.atomic.WaveformVisualizer
import com.agenticmessenger.ui.model.MeshProvenanceType
import com.agenticmessenger.ui.model.TimelineItemUiModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Molecular component: MessageBubble
 *
 * Primary conversational turn component.
 * Displays:
 * - 4-Party mesh provenance header (Human, Delegated Runner, Human-Only, System)
 * - Signer key attribution & cryptographic timestamp
 * - Natural language text body or voice dictation waveform visualizer
 * - Local inference disclosure note ("ℹ️ Answered locally from initial envelope")
 * - Structured annotation tags
 * - Hardware enclave seal indicator for sealed human-only turns
 */
@Composable
fun MessageBubble(
    message: TimelineItemUiModel.Message,
    isSelf: Boolean = false,
    modifier: Modifier = Modifier,
    onToggleAudio: () -> Unit = {}
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(message.timestamp))

    val bubbleBg = if (isSelf) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val bubbleAlignment = if (isSelf) Alignment.End else Alignment.Start

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = bubbleAlignment
    ) {
        // Provenance & Timestamp Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            ProvenanceBadge(
                provenanceType = message.provenanceType,
                signerKeySnippet = message.provenanceRecord.signerKeyId.takeLast(6)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = formattedTime,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (message.privacyLevel == PrivacyLevel.HUMAN_ONLY) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🔒",
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Bubble Content Card
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isSelf) 14.dp else 2.dp,
                        bottomEnd = if (isSelf) 2.dp else 14.dp
                    )
                )
                .background(bubbleBg)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
        ) {
            Column {
                // If audio voice dictation is present
                if (message.audioPlayback != null) {
                    WaveformVisualizer(
                        playbackState = message.audioPlayback,
                        onTogglePlay = onToggleAudio
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Natural Language text
                if (message.naturalText.isNotEmpty()) {
                    Text(
                        text = message.naturalText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Local Progressive Disclosure Notification
                if (message.localInferenceNote != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE0F2FE))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "ℹ️ ${message.localInferenceNote}",
                            fontSize = 11.sp,
                            color = Color(0xFF0369A1),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Structured Annotations
                if (message.annotations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        message.annotations.forEach { annotation ->
                            CueChip(
                                text = "${annotation.type}: ${annotation.attributes.values.firstOrNull() ?: ""}",
                                isHighlighted = true
                            )
                        }
                    }
                }

                // Human-Only Cryptographic Seal Audit
                if (message.privacyLevel == PrivacyLevel.HUMAN_ONLY) {
                    Spacer(modifier = Modifier.height(6.dp))
                    PrivacySealBadge(
                        detailedText = "Turn sealed with recipient hardware key. Inaccessible to AI agents."
                    )
                }
            }
        }
    }
}
