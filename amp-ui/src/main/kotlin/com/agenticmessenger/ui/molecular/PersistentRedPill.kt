package com.agenticmessenger.ui.molecular

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.agenticmessenger.agent.AmpAgentService
import com.agenticmessenger.agent.ContactDescriptor
import com.agenticmessenger.ui.ai.OpenAiConfig
import com.agenticmessenger.ui.model.ContactUiModel
import dev.aidos.amp.AmpConversationTurn
import kotlinx.coroutines.launch

// Typealiases to canonical TCR / Dictator ecosystem component definitions (D46)
typealias RedPillTextMode = dev.aidos.tcr.actor.RedPillTextMode
typealias PipSymbolShape = dev.aidos.tcr.actor.PipSymbolShape
typealias PillProjectionStyle = dev.aidos.tcr.actor.PillProjectionStyle
typealias ProjectedPillInstance = dev.aidos.tcr.actor.ProjectedPillInstance
typealias TargetConeHighlight = dev.aidos.tcr.actor.TargetConeHighlight
typealias RedPillHistoryEntry = dev.aidos.tcr.actor.RedPillHistoryEntry

val FastGoFastStopEasing = dev.aidos.tcr.pill.FastGoFastStopEasing

/**
 * Molecular component: User-Level Autonomous Red Pill Orchestrator.
 *
 * In accordance with D46 (TCR Component Domain Owners), Dictator / aidos-tcr is the
 * authoritative domain owner of the Red Pill component layer. AMP consumes the canonical
 * TCR component (dev.aidos.tcr.pill.PersistentRedPill) directly, wiring it with AMP's
 * agent tools and cross-chat execution services.
 */
@Composable
fun PersistentRedPill(
    aiConfig: OpenAiConfig,
    contacts: List<ContactUiModel>,
    onSendMessageToContact: suspend (contact: ContactDescriptor, text: String, ghostwrite: Boolean) -> Boolean,
    onFetchRecentMessages: suspend (mailboxId: String, limit: Int) -> List<AmpConversationTurn>,
    modifier: Modifier = Modifier,
    initialMode: RedPillTextMode = RedPillTextMode.NONE,
    ambientThoughtText: String = "Orchestrator monitoring contacts & pending actions...",
    pendingQuestionPrompt: String = "Would you like me to draft a follow-up reply?",
) {
    val scope = rememberCoroutineScope()
    var isExecuting by remember { mutableStateOf(false) }
    var executionError by remember { mutableStateOf<String?>(null) }
    val historyEntries = remember {
        mutableStateListOf(
            RedPillHistoryEntry(
                id = "h0",
                speaker = "Aidos",
                text = "Co-pilot online. Ready for cross-chat instructions.",
                isFromUser = false
            )
        )
    }

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

        historyEntries.add(
            RedPillHistoryEntry(
                id = "u_${System.currentTimeMillis()}",
                speaker = "You",
                text = q,
                isFromUser = true
            )
        )

        scope.launch {
            isExecuting = true
            executionError = null

            val res = orchestratorService.processTurn(
                incomingMessage = q,
                baseUrl = aiConfig.baseUrl,
                model = aiConfig.model,
                apiKey = aiConfig.apiKey
            )

            res.fold(
                onSuccess = { turnResult ->
                    historyEntries.add(
                        RedPillHistoryEntry(
                            id = "a_${System.currentTimeMillis()}",
                            speaker = "Aidos",
                            text = turnResult.replyText,
                            isFromUser = false,
                            executedTools = turnResult.executedToolCalls.map { it.toolName }
                        )
                    )
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "Orchestrator error"
                    executionError = errMsg
                    historyEntries.add(
                        RedPillHistoryEntry(
                            id = "err_${System.currentTimeMillis()}",
                            speaker = "System",
                            text = "Error: $errMsg",
                            isFromUser = false
                        )
                    )
                }
            )
            isExecuting = false
        }
    }

    dev.aidos.tcr.pill.PersistentRedPill(
        modifier = modifier,
        initialMode = initialMode,
        ambientThoughtText = ambientThoughtText,
        pendingQuestionPrompt = pendingQuestionPrompt,
        isExecuting = isExecuting,
        executionError = executionError,
        suggestedActions = listOf(
            "Draft follow-up reply",
            "Summarize unread",
            "Confirm 3 PM slot",
            "Ask for ETA"
        ),
        onExecutePrompt = { prompt -> executeOrchestrator(prompt) },
        historyEntries = historyEntries
    )
}
