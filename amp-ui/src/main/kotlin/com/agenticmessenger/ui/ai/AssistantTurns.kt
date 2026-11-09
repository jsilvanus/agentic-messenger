package com.agenticmessenger.ui.ai

import com.agenticmessenger.agent.AidosEndpoint
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
    endpoint: AidosEndpoint? = AidosEndpoint.discover(),
): Result<AmpAgentTurnResult> {
    if (config.useDirectOpenAi) return processTurn(incomingMessage, conversationHistory)
    if (endpoint == null) {
        return Result.failure(
            IllegalStateException("Aidos is not running. Start the Aidos desktop app, or switch the AI Copilot to the direct OpenAI endpoint.")
        )
    }
    val model = config.aidosModel.trim().ifEmpty {
        val installed = runCatching { endpoint.installedChatModels() }.getOrElse { return Result.failure(it) }
        installed.firstOrNull()
            ?: return Result.failure(IllegalStateException("Aidos has no chat model installed. Install one in the Aidos app, or set the Aidos model in the AI Copilot settings."))
    }
    return processTurnViaAidos(
        incomingMessage = incomingMessage,
        model = model,
        conversationHistory = conversationHistory,
        endpoint = endpoint,
    ) ?: Result.failure(IllegalStateException("Aidos is not running."))
}
