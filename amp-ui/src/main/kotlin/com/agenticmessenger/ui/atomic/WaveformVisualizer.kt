package com.agenticmessenger.ui.atomic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.model.AudioPlaybackUiState
import com.agenticmessenger.ui.theme.LocalProvenanceColors

/**
 * Atomic component: WaveformVisualizer
 *
 * Renders audio dictation playback with normalized amplitude waveform bars
 * and play/pause controls.
 *
 * Red Pill voice audio dictation received from the dictator bridge is played back
 * and visualized using this component.
 */
@Composable
fun WaveformVisualizer(
    playbackState: AudioPlaybackUiState,
    modifier: Modifier = Modifier,
    onTogglePlay: () -> Unit = {}
) {
    val colors = LocalProvenanceColors.current
    val barColor = colors.waveformBar
    val progressColor = colors.waveformProgress

    // Default mock amplitudes if not provided
    val sampleAmplitudes = if (playbackState.amplitudes.isNotEmpty()) {
        playbackState.amplitudes
    } else {
        listOf(0.2f, 0.4f, 0.7f, 0.9f, 0.6f, 0.8f, 0.5f, 0.3f, 0.7f, 0.85f, 0.4f, 0.2f)
    }

    val progressFraction = if (playbackState.durationMs > 0) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play / Pause Button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onTogglePlay() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (playbackState.isPlaying) "⏸" else "▶",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Waveform Canvas
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
        ) {
            val totalBars = sampleAmplitudes.size
            if (totalBars == 0) return@Canvas

            val spacing = 3.dp.toPx()
            val totalSpacing = spacing * (totalBars - 1)
            val barWidth = ((size.width - totalSpacing) / totalBars).coerceAtLeast(2.dp.toPx())

            for (i in 0 until totalBars) {
                val amp = sampleAmplitudes[i].coerceIn(0.1f, 1.0f)
                val barHeight = size.height * amp
                val left = i * (barWidth + spacing)
                val top = (size.height - barHeight) / 2f

                val isPlayed = (left / size.width) <= progressFraction
                val color = if (isPlayed) progressColor else barColor

                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Duration text
        val formattedDuration = formatDuration(
            if (playbackState.isPlaying) playbackState.currentPositionMs else playbackState.durationMs
        )
        Text(
            text = formattedDuration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
