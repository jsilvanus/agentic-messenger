package com.agenticmessenger.ui.ai

import com.agenticmessenger.agent.AmpAgentService
import dev.aidos.amp.AmpAgentTurnResult
import dev.aidos.amp.AmpConversationTurn

/**
 * Runs one assistant turn on the path the user selected: through desktop Aidos by default, or
 * through the direct OpenAI-compatible client when [OpenAiConfig.useDirectOpenAi] is on.
 */
suspend fun AmpAgentService.runAssistantTurn(
    config: OpenAiConfig,
    incomingMessage: String,
    conversationHistory: List<AmpConversationTurn> = emptyList(),
): Result<AmpAgentTurnResult> {
    if (config.useDirectOpenAi) return processTurn(incomingMessage, conversationHistory)
    return processTurnViaAidos(
        incomingMessage = incomingMessage,
        model = config.aidosModel,
        conversationHistory = conversationHistory,
    ) ?: Result.failure(
        IllegalStateException("Aidos is not running. Start the Aidos desktop app, or switch the AI Copilot to the direct OpenAI endpoint.")
    )
}
