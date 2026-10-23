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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Molecular component: ConversationInputBar
 *
 * Input composer for the messenger.
 * Includes:
 * - Multiplatform text input field
 * - Human-Only E2EE privacy toggle (locks envelope with hardware enclave keys, bypassing AI agents)
 * - Dictator Red Pill audio voice dictation trigger slot
 * - Send button
 */
@Composable
fun ConversationInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    isHumanOnly: Boolean,
    onToggleHumanOnly: () -> Unit,
    modifier: Modifier = Modifier,
    onVoiceDictationTrigger: () -> Unit = {},
    onSend: () -> Unit = {}
) {
    val lockBg = if (isHumanOnly) Color(0xFFB3261E) else Color.Transparent
    val lockBorder = if (isHumanOnly) Color(0xFFB3261E) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val lockFg = if (isHumanOnly) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        if (isHumanOnly) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFEDEB))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "🔒 Human-Only Mode Enabled: Turn will be sealed with contact's hardware key (Aidos will not see this).",
                    fontSize = 11.sp,
                    color = Color(0xFFB3261E),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Human-Only E2EE Toggle Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(lockBg)
                    .border(1.dp, lockBorder, CircleShape)
                    .clickable { onToggleHumanOnly() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔒",
                    fontSize = 14.sp,
                    color = lockFg
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text Input Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = if (isHumanOnly) "Type sealed human-only message..." else "Message or ask Aidos...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                                if (text.isNotBlank()) onSend()
                                true
                            } else {
                                false
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Red Pill Voice Audio Trigger Slot (owned by Dictator)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onVoiceDictationTrigger() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎙️",
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = text.isNotBlank()) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "➤",
                    fontSize = 14.sp,
                    color = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}
