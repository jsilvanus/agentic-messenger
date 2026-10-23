package com.agenticmessenger.ui.atomic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.theme.LocalProvenanceColors

/**
 * Atomic component: CueChip
 *
 * Ultra-condensed badge chip for displaying semantic keywords extracted
 * by local agent inferences (e.g. "[Late]", "[Car breakdown]", "[ETA 35m]").
 * Used in:
 * - Dual-View Cue-Words ticker mode
 * - Contact preview badges
 * - Aidos canvas spatial node summaries
 */
@Composable
fun CueChip(
    text: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalProvenanceColors.current
    val bg = if (isHighlighted) colors.agentRunnerBackground else colors.cueChipBackground
    val fg = if (isHighlighted) colors.agentRunnerContent else colors.cueChipContent

    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
