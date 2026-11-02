package com.agenticmessenger.mcp

import dev.aidos.amp.AmpToolDefinition
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Publishes a chosen subset of Amp's tools as an MCP server (amp roadmap M3), so another app, such as
 * Aidos, can call them over any MCP transport (the Binder transport on Android).
 *
 * Deny by default: only tools named in [exposed] are registered, so a tool added to Amp later does not
 * become reachable from outside until someone lists it here. Who may connect is the transport's job
 * (the Binder provider's caller allow-list), not this class's.
 *
 * The tools run the same handlers the in-process agent runs, so Amp's own privacy policy still applies.
 */
class AmpMcpServer(
    tools: List<AmpToolDefinition>,
    exposed: Set<String> = CHAT_TOOLS,
    serverName: String = "agentic-messenger",
    serverVersion: String = "0.1.0",
    /**
     * `send_chat_message` goes out as the agent delegate by default and as the human ("Human Direct")
     * when the caller passes `ghostwrite=true`. True keeps both modes; false removes the human one, for a
     * deployment that must never let another app speak as the user.
     */
    allowSendAsHuman: Boolean = true,
) {
    val server: Server = Server(
        Implementation(name = serverName, version = serverVersion),
        ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
    )

    /** The tool names actually registered (those in [exposed] that Amp defines). */
    val toolNames: List<String>

    init {
        val published = tools.filter { it.name in exposed }
        published.forEach { tool ->
            server.addTool(
                name = tool.name,
                description = tool.description,
                inputSchema = (if (allowSendAsHuman) tool.inputSchema else tool.inputSchema.withoutImpersonation()).toToolSchema(),
            ) { request ->
                val result = try {
                    val arguments = request.arguments ?: JsonObject(emptyMap())
                    tool.handler(if (allowSendAsHuman) arguments else arguments.withoutImpersonation())
                } catch (e: Exception) {
                    // The message only: an exception text can carry arguments, and MCP errors reach another app.
                    return@addTool CallToolResult(listOf(TextContent("Tool '${tool.name}' failed")), isError = true)
                }
                CallToolResult(listOf(TextContent(result.outputText)), isError = result.isError)
            }
        }
        toolNames = published.map { it.name }
    }

    companion object {
        /**
         * The chat tools: no calendar. `query_chat_messages` returns message text and `send_chat_message`
         * sends as the user, so the transport must only admit callers the user has allowed.
         */
        val CHAT_TOOLS: Set<String> = setOf("get_contact_status", "query_chat_messages", "send_chat_message")
    }
}

/** Removes `ghostwrite`, so every send is badged as the agent delegate (see `allowSendAsHuman`). */
private fun JsonObject.withoutImpersonation(): JsonObject {
    val properties = this["properties"] as? JsonObject
    return if (properties != null && "ghostwrite" in properties) {
        // A tool schema: drop the property.
        JsonObject(this + ("properties" to JsonObject(properties - "ghostwrite")))
    } else {
        // Call arguments: drop the key.
        JsonObject(this - "ghostwrite")
    }
}

private fun JsonObject.toToolSchema(): ToolSchema = ToolSchema(
    properties = this["properties"]?.jsonObject,
    required = (this["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
)
