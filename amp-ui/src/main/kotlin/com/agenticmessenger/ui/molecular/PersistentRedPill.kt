package com.agenticmessenger.ui.molecular

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agenticmessenger.agent.AmpAgentService
import com.agenticmessenger.agent.ContactDescriptor
import com.agenticmessenger.ui.ai.OpenAiConfig
import com.agenticmessenger.ui.model.ContactUiModel
import dev.aidos.amp.AmpAgentTurnResult
import dev.aidos.amp.AmpConversationTurn
import kotlinx.coroutines.launch

/**
 * Molecular component inspired by Dictator's Ember Red Pill (0xFFEF4444):
 * A persistent interactive capsule representing the User-Level AI Orchestrator.
 *
 * Allows the user to execute cross-chat instructions:
 * - "Send message X to Bob"
 * - "What did Bob say in our chat?"
 * - "Check if I am free at 5 PM"
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersistentRedPill(
    aiConfig: OpenAiConfig,
    contacts: List<ContactUiModel>,
    onSendMessageToContact: suspend (contact: ContactDescriptor, text: String, ghostwrite: Boolean) -> Boolean,
    onFetchRecentMessages: suspend (mailboxId: String, limit: Int) -> List<AmpConversationTurn>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var userPromptInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var latestResult by remember { mutableStateOf<AmpAgentTurnResult?>(null) }
    var executionError by remember { mutableStateOf<String?>(null) }

    // Pulsing animation for the red pip (Ember aesthetic)
    val infiniteTransition = rememberInfiniteTransition(label = "pipPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Wire orchestrator service with cross-chat callbacks
    val orchestratorService = remember(contacts) {
        AmpAgentService(
            contactLookup = { query ->
                contacts.firstOrNull {
                    it.displayName.contains(query, ignoreCase = true) ||
                            it.handle.contains(query, ignoreCase = true) ||
                            it.principalId.contains(query, ignoreCase = true)
                }?.let {
                    ContactDescriptor(
                        id = it.contactId,
                        displayName = it.displayName,
                        mailboxId = it.mailboxId,
                        principalId = it.principalId,
                        x25519PublicKeyHex = it.x25519PublicKeyHex
                    )
                }
            },
            messageFetcher = onFetchRecentMessages,
            messageSender = onSendMessageToContact,
        )
    }

    fun executeOrchestrator(prompt: String) {
        val q = prompt.trim()
        if (q.isBlank() || isExecuting) return

        scope.launch {
            isExecuting = true
            executionError = null
            latestResult = null
            userPromptInput = ""

            val res = orchestratorService.processTurnViaAidos(
                incomingMessage = q,
                model = aiConfig.aidosModel,
            ) ?: Result.failure(IllegalStateException("Aidos is not running. Start the Aidos desktop app; the assistant's models run there."))

            res.fold(
                onSuccess = { turnResult ->
                    latestResult = turnResult
                },
                onFailure = { err ->
                    executionError = err.message ?: "Orchestrator error"
                }
            )
            isExecuting = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // 1. The Persistent Pill Capsule Header
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E24),
            modifier = Modifier
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626))),
                    shape = RoundedCornerShape(20.dp)
                )
                .clip(RoundedCornerShape(20.dp))
                .clickable { isExpanded = !isExpanded }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Luminous Red Pip (Dictator Ember)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(pulseAlpha)
                            .background(Color(0xFFEF4444), shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔴 Aidos Orchestrator",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Cross-Chat AI",
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444).copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isExpanded) "Hide ▲" else "Open Co-Pilot ▼",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 2. Expanded Co-Pilot Panel (HUD)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFEF4444).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Command your user-level agent across all chats (Aidos core):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Input & Run Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = userPromptInput,
                            onValueChange = { userPromptInput = it },
                            placeholder = {
                                Text(
                                    "e.g. 'Tell Bob I\\'ll be 10 min late', 'What did Bob say?'",
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { executeOrchestrator(userPromptInput) },
                            enabled = !isExecuting && userPromptInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Run", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Action Chips
                    Text(
                        text = "Suggested actions:",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val targetContact = contacts.firstOrNull()?.displayName ?: "Bob"
                        val chips = listOf(
                            "💬 Tell $targetContact I'll be 10 minutes late",
                            "🔍 What did $targetContact say in our chat?",
                            "📅 Check if I am free at 5 PM"
                        )
                        for (chipText in chips) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                    .clickable {
                                        val prompt = chipText.substringAfter(" ")
                                        userPromptInput = prompt
                                        executeOrchestrator(prompt)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(chipText, fontSize = 10.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Execution Results or Error
                    if (executionError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEE2E2), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text("⚠️ $executionError", fontSize = 11.sp, color = Color(0xFF991B1B))
                        }
                    }

                    if (latestResult != null) {
                        val res = latestResult!!
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0FDF4), shape = RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF86EFAC), shape = RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                if (res.executedToolCalls.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Executed Tools:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        for (tc in res.executedToolCalls) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFBBF7D0))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(tc.toolName, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF14532D))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(
                                    text = res.replyText,
                                    fontSize = 11.sp,
                                    color = Color(0xFF14532D),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
