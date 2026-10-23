package com.agenticmessenger.ui.atomic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.theme.LocalProvenanceColors

/**
 * Atomic component: PrivacySealBadge
 *
 * Indicator rendered when a conversation turn or card is sealed with
 * hardware-enclave human-only keys (RFC 9420 / StrongBox), indicating that
 * neither party's AI assistants can inspect or process the payload.
 */
@Composable
fun PrivacySealBadge(
    modifier: Modifier = Modifier,
    detailedText: String? = null
) {
    val colors = LocalProvenanceColors.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.humanOnlyBackground)
            .border(
                width = 1.dp,
                color = colors.humanOnlyContent.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🛡️",
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = detailedText ?: "Hardware Enclave Sealed (No Agent Access)",
                color = colors.humanOnlyContent,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp
            )
        }
    }
}
