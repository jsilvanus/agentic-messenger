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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.model.DisclosureStatus
import com.agenticmessenger.ui.theme.LocalProvenanceColors

/**
 * Molecular component: ApprovalCard
 *
 * 1-Tap Progressive Disclosure Authorization Card.
 *
 * Used both in:
 * a) Standalone Messenger conversation stream
 * b) Aidos Desktop as a Generative UI card on canvas or in run timeline
 *
 * Implements Scenario 2 ("Jack's Timeline — Hands-Free Voice Dictation & Action Required"):
 * "John's assistant is requesting your Estimated Arrival Time. Share your navigation ETA (~35 min)?"
 * [ 🔘 Share ETA (35 min) ]       [ ✕ Decline ]
 */
@Composable
fun ApprovalCard(
    requestId: String,
    topicKey: String,
    promptText: String,
    proposedValue: String? = null,
    status: DisclosureStatus = DisclosureStatus.PENDING,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onApprove: (requestId: String) -> Unit = {},
    onDecline: (requestId: String) -> Unit = {}
) {
    val colors = LocalProvenanceColors.current

    val borderColor = when (status) {
        DisclosureStatus.PENDING -> Color(0xFFF59E0B) // Amber
        DisclosureStatus.APPROVED -> Color(0xFF10B981) // Emerald
        DisclosureStatus.DECLINED -> Color(0xFF6B7280) // Gray
    }

    val bannerBg = when (status) {
        DisclosureStatus.PENDING -> Color(0xFFFEF3C7)
        DisclosureStatus.APPROVED -> Color(0xFFD1FAE5)
        DisclosureStatus.DECLINED -> Color(0xFFF3F4F6)
    }

    val bannerFg = when (status) {
        DisclosureStatus.PENDING -> Color(0xFF92400E)
        DisclosureStatus.APPROVED -> Color(0xFF065F46)
        DisclosureStatus.DECLINED -> Color(0xFF374151)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.5.dp,
                color = borderColor.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (status) {
                        DisclosureStatus.PENDING -> "⚡ Action Required"
                        DisclosureStatus.APPROVED -> "✅ Approved & Disclosed"
                        DisclosureStatus.DECLINED -> "✕ Authorization Declined"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = bannerFg
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Topic: $topicKey",
                    fontSize = 11.sp,
                    color = bannerFg.copy(alpha = 0.8f)
                )
            }

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (proposedValue != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Escrowed Value: $proposedValue",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Actions
                if (status == DisclosureStatus.PENDING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(24.dp).width(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onApprove(requestId) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF006A60)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "🔘 Approve & Share",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = { onDecline(requestId) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "✕ Decline",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
