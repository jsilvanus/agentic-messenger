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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.atomic.CueChip
import com.agenticmessenger.ui.atomic.PresenceIndicator
import com.agenticmessenger.ui.model.ContactUiModel

/**
 * Molecular component: ContactRow
 *
 * Contact list item in the contacts directory or spatial canvas node preview.
 * Displays:
 * - Contact avatar initial with presence badge
 * - Contact Name & DID handle
 * - Aidos Assistant status indicator
 * - Latest cue-word badge summary (e.g. [Late: Car Breakdown])
 * - Unread message pill & Pending progressive disclosure badge
 */
@Composable
fun ContactRow(
    contact: ContactUiModel,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val bg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with Presence
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.displayName.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            PresenceIndicator(
                presence = contact.presence,
                pulseAnimation = contact.pendingDisclosureCount > 0
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name, Handle, Latest Snippet
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (contact.isAidosActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF006A60).copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "🤖 Aidos",
                            fontSize = 10.sp,
                            color = Color(0xFF006A60),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (contact.latestCueBadge != null) {
                CueChip(
                    text = contact.latestCueBadge,
                    isHighlighted = true
                )
            } else if (contact.latestSnippet != null) {
                Text(
                    text = contact.latestSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Badges: Pending Disclosures & Unread count
        Column(horizontalAlignment = Alignment.End) {
            if (contact.pendingDisclosureCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF59E0B))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⚡ ${contact.pendingDisclosureCount}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (contact.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = contact.unreadCount.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
