package com.agenticmessenger.ui.molecular

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.atomic.CueChip
import com.agenticmessenger.ui.atomic.ProvenanceBadge
import com.agenticmessenger.ui.model.MeshProvenanceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Molecular component: CueTickerRow
 *
 * Renders an ultra-condensed semantic cue row for executive/glance mode.
 * Each message turn is presented as:
 * [Time] [Sender Badge] -> [Cue1] [Cue2] [Cue3]
 */
@Composable
fun CueTickerRow(
    timestamp: Long,
    senderName: String,
    provenanceType: MeshProvenanceType,
    cueWords: List<String>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(timestamp))

    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedTime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            ProvenanceBadge(
                provenanceType = provenanceType,
                label = senderName
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (cueWords.isEmpty()) {
                    Text(
                        text = "• • •",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    cueWords.forEach { cue ->
                        CueChip(text = cue)
                    }
                }
            }
        }
    }
}
