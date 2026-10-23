package com.agenticmessenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.ui.model.ContactFilter
import com.agenticmessenger.ui.model.ContactUiModel
import com.agenticmessenger.ui.molecular.ContactRow
import com.agenticmessenger.ui.presenter.ContactsPresenter

/**
 * Screen: ContactsScreen (Compose Multiplatform)
 *
 * Contacts Directory with:
 * - Search bar
 * - Filter tabs (All, Aidos Active, Action Required)
 * - Progressive disclosure escalation banner
 * - Presence indicators & Cue summary previews
 */
@Composable
fun ContactsScreen(
    presenter: ContactsPresenter,
    modifier: Modifier = Modifier,
    onContactClick: (contact: ContactUiModel) -> Unit = {}
) {
    val state by presenter.state.collectAsState()
    val filteredContacts = presenter.filteredContacts()
    val totalPendingDisclosures = state.contacts.sumOf { it.pendingDisclosureCount }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💬 Mesh Conversations",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF006A60).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🟢 Relay Active",
                            fontSize = 11.sp,
                            color = Color(0xFF006A60),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = "🔍 Search contacts or handles...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = { presenter.onSearchQueryChanged(it) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChipItem(
                        label = "All (${state.contacts.size})",
                        isSelected = state.activeFilter == ContactFilter.ALL,
                        onClick = { presenter.onFilterChanged(ContactFilter.ALL) }
                    )

                    FilterChipItem(
                        label = "🤖 Aidos Active",
                        isSelected = state.activeFilter == ContactFilter.AIDOS_ACTIVE,
                        onClick = { presenter.onFilterChanged(ContactFilter.AIDOS_ACTIVE) }
                    )

                    FilterChipItem(
                        label = "⚡ Action Required ($totalPendingDisclosures)",
                        isSelected = state.activeFilter == ContactFilter.ACTION_REQUIRED,
                        isWarning = totalPendingDisclosures > 0,
                        onClick = { presenter.onFilterChanged(ContactFilter.ACTION_REQUIRED) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Action Required Banner
            if (totalPendingDisclosures > 0 && state.activeFilter != ContactFilter.ACTION_REQUIRED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF3C7))
                        .clickable { presenter.onFilterChanged(ContactFilter.ACTION_REQUIRED) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ $totalPendingDisclosures progressive disclosure request(s) waiting for your authorization",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "View →",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Contact List
            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No contacts found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredContacts, key = { it.contactId }) { contact ->
                        ContactRow(
                            contact = contact,
                            isSelected = contact.contactId == state.selectedContactId,
                            onClick = {
                                presenter.selectContact(contact.contactId)
                                onContactClick(contact)
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    isWarning: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected && isWarning -> Color(0xFFF59E0B)
        isSelected -> MaterialTheme.colorScheme.primary
        isWarning -> Color(0xFFFEF3C7)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val fg = when {
        isSelected -> Color.White
        isWarning -> Color(0xFF92400E)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = fg
        )
    }
}
