package com.agenticmessenger.ui.atomic

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.model.PresenceState
import com.agenticmessenger.ui.theme.LocalProvenanceColors

/**
 * Atomic component: PresenceIndicator
 *
 * Renders contact presence with optional pulse wave animation.
 * Supports:
 * - Direct contact presence (Online, Busy, Idle, Offline)
 * - Aidos Assistant active badge ("Active · Aidos")
 * - Soft pulsing presence wave for spatial canvas nodes
 */
@Composable
fun PresenceIndicator(
    presence: PresenceState,
    modifier: Modifier = Modifier,
    isAidosActive: Boolean = false,
    showLabel: Boolean = false,
    pulseAnimation: Boolean = false
) {
    val colors = LocalProvenanceColors.current

    val baseColor = when (presence) {
        PresenceState.ONLINE -> colors.pulseActive
        PresenceState.BUSY -> Color(0xFFEF4444)
        PresenceState.IDLE -> Color(0xFFF59E0B)
        PresenceState.OFFLINE -> Color(0xFF9CA3AF)
    }

    val pulseScale by if (pulseAnimation && presence == PresenceState.ONLINE) {
        val infiniteTransition = rememberInfiniteTransition()
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (pulseAnimation && presence == PresenceState.ONLINE) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(pulseScale)
                        .background(baseColor.copy(alpha = 0.35f), shape = CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(baseColor, shape = CircleShape)
            )
        }

        if (showLabel) {
            Spacer(modifier = Modifier.width(6.dp))
            val labelText = buildString {
                append(when (presence) {
                    PresenceState.ONLINE -> "Active"
                    PresenceState.BUSY -> "Busy"
                    PresenceState.IDLE -> "Away"
                    PresenceState.OFFLINE -> "Offline"
                })
                if (isAidosActive) {
                    append(" · Aidos")
                }
            }
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}
