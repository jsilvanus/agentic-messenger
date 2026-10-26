package com.agenticmessenger.ui.ai

import kotlinx.serialization.Serializable

/**
 * Configuration for an OpenAI-compatible API endpoint (e.g. Ollama, Aidos, vLLM, LocalAI).
 */
@Serializable
data class OpenAiConfig(
    val host: String = "localhost",
    val port: Int = 11434, // Default Ollama port (8000 for Aidos / LocalAI)
    val model: String = "llama3",
    /** The model id in Aidos (`/v1/models`) that agent turns use; a provider's models are `<provider>:<model>`. */
    val aidosModel: String = "ollama:llama3",
    /**
     * User selection. Off (the default): turns run through the desktop Aidos app, which owns the
     * models and enforces where inference may go. On: this client talks to the endpoint above
     * itself (Ollama, vLLM, LocalAI), outside Aidos's locality rules.
     */
    val useDirectOpenAi: Boolean = false,
    val apiKey: String = "",
    val isEnabled: Boolean = true,
    val systemPrompt: String = "You are Aidos, an intelligent privacy-preserving personal AI assistant participating in a collaborative end-to-end encrypted mesh conversation. Keep responses helpful, concise, and direct."
) {
    val normalizedHost: String
        get() {
            val trimmed = host.trim().removeSuffix("/")
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }

    val baseUrl: String
        get() = "$normalizedHost:$port"

    val chatCompletionsUrl: String
        get() = "$baseUrl/v1/chat/completions"

    val modelsUrl: String
        get() = "$baseUrl/v1/models"
}
