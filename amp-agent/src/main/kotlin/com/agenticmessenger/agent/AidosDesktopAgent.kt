package com.agenticmessenger.agent

import dev.aidos.amp.AmpAgentTurnResult
import dev.aidos.amp.AmpConversationTurn
import dev.aidos.amp.AmpToolDefinition
import dev.aidos.amp.AmpToolExecutionResult
import dev.aidos.amp.DefaultJavaHttpTransport
import dev.aidos.amp.ExecutedToolCall
import dev.aidos.amp.HttpTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/** Where the desktop Aidos app is listening and the token it accepts (`~/.aidos/engine.json`). */
data class AidosEndpoint(val baseUrl: String, val token: String) {
    companion object {
        /** The default discovery file, or the one named by `AIDOS_ENGINE_DISCOVERY`. */
        fun defaultDiscoveryFile(): File =
            System.getenv("AIDOS_ENGINE_DISCOVERY")?.let(::File)
                ?: File(System.getProperty("user.home"), ".aidos/engine.json")

        /** Null when Aidos is not running (no file), not ready, or not offering the agent API. */
        fun discover(file: File = defaultDiscoveryFile()): AidosEndpoint? = runCatching {
            val o = Json.parseToJsonElement(file.readText()).jsonObject
            if (o["status"]?.jsonPrimitive?.contentOrNull != "READY") return null
            if (o["agentApi"] == null) return null
            AidosEndpoint(
                o.getValue("baseUrl").jsonPrimitive.content.trimEnd('/'),
                o.getValue("token").jsonPrimitive.content,
            )
        }.getOrNull()
    }
}

/**
 * Runs an agent turn on the desktop Aidos app (`/v1/agent`, aidos D48) instead of in this process.
 *
 * Aidos owns the loop and the model; Amp owns the messaging domain, so the tools run here: when the
 * runtime asks for one, [AmpToolDefinition.handler] is called and its result posted back. Amp's own
 * policy still applies because the handlers are the same ones the embedded runner would call.
 */
class AidosDesktopAgent(
    private val endpoint: AidosEndpoint,
    private val transport: HttpTransport = DefaultJavaHttpTransport(),
    /** Guards against a runtime that keeps asking for tools. */
    private val maxToolRounds: Int = 16,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun executeTurn(
        model: String,
        systemPrompt: String,
        incomingMessage: String,
        history: List<AmpConversationTurn>,
        tools: List<AmpToolDefinition>,
    ): Result<AmpAgentTurnResult> = runCatching {
        val byName = tools.associateBy { it.name }
        var response = post("/v1/agent/turns", buildJsonObject {
            put("model", model)
            put("message", incomingMessage)
            put("systemPrompt", systemPrompt)
            put("history", buildJsonArray {
                history.forEach { add(buildJsonObject { put("role", it.role); put("text", it.text); it.senderId?.let { s -> put("senderId", s) } }) }
            })
            put("tools", buildJsonArray {
                tools.forEach { add(buildJsonObject { put("name", it.name); put("description", it.description); put("inputSchema", it.inputSchema) }) }
            })
        })
        var rounds = 0
        while (response.status == "tool_calls") {
            check(++rounds <= maxToolRounds) { "Aidos asked for tools more than $maxToolRounds times" }
            val results = response.toolCalls.map { call ->
                val tool = byName[call.name]
                val result = if (tool == null) {
                    AmpToolExecutionResult("Tool '${call.name}' not found in registry", isError = true)
                } else {
                    try {
                        tool.handler(call.arguments)
                    } catch (e: Exception) {
                        AmpToolExecutionResult("Tool '${call.name}' threw error: ${e.message}", isError = true)
                    }
                }
                buildJsonObject { put("callId", call.callId); put("output", result.outputText); put("isError", result.isError) }
            }
            response = post("/v1/agent/runs/${response.runId}/tool-results", buildJsonObject { put("results", JsonArray(results)) })
        }
        check(response.status == "completed") { "Aidos agent run failed: ${response.error ?: response.status}" }
        AmpAgentTurnResult(response.reply.orEmpty(), response.executed, response.steps)
    }

    private class Step(
        val status: String,
        val runId: String?,
        val reply: String?,
        val error: String?,
        val steps: Int,
        val toolCalls: List<Call>,
        val executed: List<ExecutedToolCall>,
    )

    private class Call(val callId: String, val name: String, val arguments: JsonObject)

    private suspend fun post(path: String, body: JsonObject): Step {
        val raw = transport.postJson(endpoint.baseUrl + path, mapOf("Authorization" to "Bearer ${endpoint.token}"), body.toString())
        val o = json.parseToJsonElement(raw).jsonObject
        o["error"]?.takeIf { it is JsonObject }?.let { err ->
            error("Aidos: ${err.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "request failed"}")
        }
        return Step(
            status = o.getValue("status").jsonPrimitive.content,
            runId = o["runId"]?.jsonPrimitive?.contentOrNull,
            reply = o["reply"]?.jsonPrimitive?.contentOrNull,
            error = o["error"]?.jsonPrimitive?.contentOrNull,
            steps = o["steps"]?.jsonPrimitive?.int ?: 0,
            toolCalls = o["toolCalls"]?.jsonArray.orEmpty().map {
                val c = it.jsonObject
                Call(c.getValue("callId").jsonPrimitive.content, c.getValue("name").jsonPrimitive.content, c["arguments"]?.jsonObject ?: JsonObject(emptyMap()))
            },
            executed = o["executedToolCalls"]?.jsonArray.orEmpty().map {
                val c = it.jsonObject
                ExecutedToolCall(c.getValue("toolName").jsonPrimitive.content, c["argumentsJson"]?.jsonPrimitive?.contentOrNull.orEmpty(), c["resultText"]?.jsonPrimitive?.contentOrNull.orEmpty())
            },
        )
    }
}
