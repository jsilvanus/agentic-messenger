package com.agenticmessenger.ui.molecular

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import com.agenticmessenger.ui.model.ConversationViewMode

/**
 * Molecular component: DualViewToggle
 *
 * Provides instant switching between:
 * - Natural Language Stream (standard rich conversation timeline)
 * - Cue-Words Ticker (ultra-dense semantic keyword stream)
 */
@Composable
fun DualViewToggle(
    currentMode: ConversationViewMode,
    modifier: Modifier = Modifier,
    onModeChanged: (ConversationViewMode) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(20.dp)
            )
            .padding(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Natural Stream Tab
            val isNatural = currentMode == ConversationViewMode.NATURAL_STREAM
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isNatural) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { onModeChanged(ConversationViewMode.NATURAL_STREAM) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💬 Stream",
                    fontSize = 11.sp,
                    fontWeight = if (isNatural) FontWeight.Bold else FontWeight.Normal,
                    color = if (isNatural) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Cue Words Tab
            val isTicker = currentMode == ConversationViewMode.CUE_WORDS_TICKER
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isTicker) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { onModeChanged(ConversationViewMode.CUE_WORDS_TICKER) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🏷️ Cue Ticker",
                    fontSize = 11.sp,
                    fontWeight = if (isTicker) FontWeight.Bold else FontWeight.Normal,
                    color = if (isTicker) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
