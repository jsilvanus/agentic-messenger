package com.agenticmessenger.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.desktop.model.DesktopChatMessage
import com.agenticmessenger.ui.atomic.ProvenanceBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scrollable bubble feed for Desktop Chat Window.
 *
 * Implements:
 * - Distinct colors for sent vs received messages
 * - Delivery timestamps
 * - Delivery ACK status indicator
 * - Human-only E2EE badge
 */
@Composable
fun ChatBubbleFeed(
    messages: List<DesktopChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (messages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🔒 End-to-End Encrypted Session Initialized",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type a message below or press Enter to send.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                DesktopMessageBubble(msg)
            }
        }
    }
}

@Composable
private fun DesktopMessageBubble(message: DesktopChatMessage) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(message.timestamp))

    val isSent = message.isSentByMe

    // Distinct background colors:
    // Sent: Deep Indigo / Violet primaryContainer
    // Received: Cool Slate surfaceVariant
    val bubbleBg = if (isSent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val bubbleContentColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val alignment = if (isSent) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Header info: Sender name, timestamp, provenance
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (!isSent) {
                Text(
                    text = message.senderDisplayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            ProvenanceBadge(
                provenanceType = message.provenanceType,
                label = if (message.isHumanOnly) "🔒 Sealed Human-Only" else null
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = formattedTime,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            if (isSent) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (message.isDelivered) "✓ Delivered" else "⌛ Sending",
                    fontSize = 10.sp,
                    color = if (message.isDelivered) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
            }
        }

        // Bubble shape & card
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isSent) 12.dp else 2.dp,
                        bottomEnd = if (isSent) 2.dp else 12.dp
                    )
                )
                .background(bubbleBg)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = bubbleContentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp
            )
        }
    }
}
