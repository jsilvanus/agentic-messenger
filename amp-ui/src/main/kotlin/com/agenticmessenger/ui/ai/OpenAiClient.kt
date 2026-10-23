package com.agenticmessenger.ui.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Logger

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @EncodeDefault val stream: Boolean = false,
    @EncodeDefault val temperature: Double = 0.7
)

@Serializable
data class OpenAiMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

@Serializable
data class OpenAiChatResponse(
    val id: String = "",
    val choices: List<OpenAiChoice> = emptyList()
)

@Serializable
data class OpenAiChoice(
    val index: Int = 0,
    val message: OpenAiMessage? = null
)

/**
 * Lightweight client for any OpenAI-compatible Chat Completions API
 * (Ollama, Aidos, vLLM, LocalAI, LM Studio).
 * Powered by standard Java HTTP client with zero external network dependencies.
 */
open class OpenAiClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
) {
    private val logger = Logger.getLogger(OpenAiClient::class.java.name)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Checks if the configured OpenAI-compatible endpoint is reachable.
     */
    open suspend fun testConnection(config: OpenAiConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.modelsUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()

            if (config.apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${config.apiKey}")
            }

            val response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
            val statusCode = response.statusCode()

            if (statusCode in 200..299) {
                Result.success("Connected to ${config.baseUrl} (HTTP $statusCode)")
            } else {
                Result.failure(Exception("HTTP $statusCode from ${config.modelsUrl}"))
            }
        } catch (e: Exception) {
            logger.warning("OpenAI connection check failed for ${config.baseUrl}: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sends a chat completion prompt to the OpenAI-compatible endpoint.
     */
    open suspend fun chatCompletion(
        config: OpenAiConfig,
        messages: List<OpenAiMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = OpenAiChatRequest(
                model = config.model,
                messages = messages
            )
            val jsonPayload = json.encodeToString(OpenAiChatRequest.serializer(), requestBody)

            val reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(config.chatCompletionsUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))

            if (config.apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${config.apiKey}")
            }

            val response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
            val statusCode = response.statusCode()

            if (statusCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP $statusCode: ${response.body()}"))
            }

            val responseBody = response.body()
            val chatResponse = json.decodeFromString(OpenAiChatResponse.serializer(), responseBody)
            val reply = chatResponse.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Empty response choices from model"))

            Result.success(reply.trim())
        } catch (e: Exception) {
            logger.severe("OpenAI chat completion error at ${config.chatCompletionsUrl}: ${e.message}")
            Result.failure(e)
        }
    }
}
