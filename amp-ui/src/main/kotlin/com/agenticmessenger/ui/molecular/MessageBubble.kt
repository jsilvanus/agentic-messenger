package com.agenticmessenger.ui.molecular

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.core.model.AttachmentType
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
 * - Sealed Voice Notes (🛡️ Sealed Voice, 📝 Shielded STT, authentic audio, opt-out toggle)
 * - File attachments (Documents, Images, Generic blobs)
 * - Recipient progressive unshielding controls ("🔓 Unshield for my AI")
 * - Sender disclosure receipt audit indicator ("📡 Disclosed by recipient to local AI")
 * - Structured annotation tags and local inference notes
 */
@Composable
fun MessageBubble(
    message: TimelineItemUiModel.Message,
    isSelf: Boolean = false,
    modifier: Modifier = Modifier,
    onToggleAudio: () -> Unit = {},
    onToggleStt: (messageId: String) -> Unit = {},
    onUnshieldForAi: (messageId: String) -> Unit = {}
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
                // 1. Sealed Voice Header Badges (🛡️ Shield + 📝 STT symbols)
                if (message.isShieldedVoice) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🛡️", fontSize = 11.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Sealed Voice",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📝", fontSize = 11.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Shielded STT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB)
                                )
                            }
                        }
                    }
                }

                // 2. Authentic Voice Audio Waveform (No TTS)
                if (message.audioPlayback != null) {
                    WaveformVisualizer(
                        playbackState = message.audioPlayback,
                        onTogglePlay = onToggleAudio
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 3. Sealed Voice STT Transcript (with recipient opt-out toggle)
                if (message.isShieldedVoice) {
                    val transcript = message.shieldedSttTranscript ?: message.naturalText
                    if (message.isSttVisible && transcript.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "📝 Shielded On-Device Transcript (Quarantined from AI)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Hide / Audio Only",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { onToggleStt(message.id) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = transcript,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        // Recipient opted out of transcript display
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onToggleStt(message.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📝 Transcript Hidden (Audio Only)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("Show", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else if (message.naturalText.isNotEmpty()) {
                    // Standard Natural Language text
                    Text(
                        text = message.naturalText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 4. File Attachments (Documents, Images, Blobs)
                val nonAudioAttachments = message.attachments.filter { it.type != AttachmentType.VOICE_NOTE }
                if (nonAudioAttachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        nonAudioAttachments.forEach { att ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when (att.type) {
                                        AttachmentType.DOCUMENT -> "📄"
                                        AttachmentType.IMAGE -> "🖼️"
                                        else -> "📎"
                                    }
                                    Text(icon, fontSize = 18.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(att.fileName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text("${att.mimeType} • ${formatBytes(att.sizeBytes)}", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    if (message.privacyLevel == PrivacyLevel.HUMAN_ONLY) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFEF2F2),
                                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                "🛡️ Sealed",
                                                fontSize = 9.sp,
                                                color = Color(0xFFDC2626),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Progressive Unshielding & Disclosure Receipts
                if (message.privacyLevel == PrivacyLevel.HUMAN_ONLY) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Case A: Recipient view & not yet unshielded -> Allow recipient to unshield for their AI
                    if (!isSelf && !message.isUnshieldedForAi) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUnshieldForAi(message.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔓", fontSize = 11.sp)
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        "Unshield for my AI",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                    Text(
                                        "Grants your local AI access. Transmits disclosure receipt to sender.",
                                        fontSize = 9.sp,
                                        color = Color(0xFF3B82F6)
                                    )
                                }
                            }
                        }
                    }

                    // Case B: Recipient unshielded for their AI
                    if (!isSelf && message.isUnshieldedForAi) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔓", fontSize = 11.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Unshielded for Local AI (Disclosure receipt sent to sender)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }

                    // Case C: Sender copy — recipient unshielded it
                    if (isSelf && message.disclosureReceiptReceived) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📡", fontSize = 11.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Disclosed by recipient to local AI (Sender copy remains sealed)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }

                    // Case D: Standard sealed notice if not unshielded
                    if (!message.isUnshieldedForAi && !message.disclosureReceiptReceived) {
                        PrivacySealBadge(
                            detailedText = "Hardware Enclave Sealed (Quarantined from AI models)"
                        )
                    }
                }

                // 6. Local Progressive Disclosure Notification
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

                // 7. Structured Annotations
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
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
