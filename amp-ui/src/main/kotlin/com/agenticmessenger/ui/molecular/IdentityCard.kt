package com.agenticmessenger.ui.molecular

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.atomic.PresenceIndicator
import com.agenticmessenger.ui.model.PresenceState

/**
 * Molecular component: IdentityCard
 *
 * Displays cryptographic identity information:
 * - Principal DID
 * - Device Key ID
 * - Blinded Mailbox Token (`HMAC(epoch, device_id)`)
 * - Relay connection status
 * - Hardware Enclave indicator
 */
@Composable
fun IdentityCard(
    principalId: String,
    deviceKeyId: String,
    mailboxId: String,
    isConnected: Boolean,
    isHardwareEnclave: Boolean,
    modifier: Modifier = Modifier,
    onCopyMailbox: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PresenceIndicator(
                    presence = if (isConnected) PresenceState.ONLINE else PresenceState.OFFLINE
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConnected) "Relay Connected" else "Relay Disconnected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isHardwareEnclave) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🛡️ StrongBox Active",
                            fontSize = 10.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Principal Identity:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = principalId,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Blinded Mailbox Token:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { onCopyMailbox() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mailboxId.ifEmpty { "Pending epoch derivation..." },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "📋",
                    fontSize = 12.sp
                )
            }
        }
    }
}
