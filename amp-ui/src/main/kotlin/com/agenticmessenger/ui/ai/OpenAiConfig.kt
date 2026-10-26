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
    val apiKey: String = "",
    val isEnabled: Boolean = true,
    /** Use the Aidos desktop app's agent runtime when it is running; false selects the endpoint above. */
    val useAidosDesktop: Boolean = true,
    /** Aidos model id; blank picks the first installed one. */
    val aidosModel: String = "",
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
