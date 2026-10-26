package com.agenticmessenger.agent

import dev.aidos.amp.AmpAgentRunner
import dev.aidos.amp.AmpAgentTurnResult
import dev.aidos.amp.AmpConversationTurn
import dev.aidos.amp.AmpToolDefinition
import dev.aidos.amp.AmpToolExecutionResult
import dev.aidos.amp.OpenAiHttpModelAdapter
import dev.aidos.kernel.ModelAdapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Resolved contact reference for cross-chat orchestration.
 */
@Serializable
data class ContactDescriptor(
    val id: String,
    val displayName: String,
    val mailboxId: String,
    val principalId: String,
    val x25519PublicKeyHex: String = "",
)

/**
 * High-level Agentic Messenger Platform service hosted on the AMP side,
 * executing systemic agentic workflows powered by Aidos's `:agent:amp` core.
 *
 * Supports both conversation-specific reasoning and cross-chat user-level orchestration.
 *
 * Dual Mode Messaging:
 * - Default: Dispatches as autonomous Agent Delegate (badged to recipient as 🤖 Delegated Runner).
 * - Option: Dispatches as Ghostwriter / "Send as Me" (badged to recipient as 👤 Human Direct).
 * - Constraint: Never creates sealed messages (PrivacyLevel is always AGENT_ACCESSIBLE).
 */
class AmpAgentService(
    val systemPrompt: String = DEFAULT_AMP_SYSTEM_PROMPT,
    customTools: List<AmpToolDefinition> = emptyList(),
    val customAdapter: ModelAdapter? = null,
    val contactLookup: ((nameOrQuery: String) -> ContactDescriptor?)? = null,
    val messageFetcher: (suspend (mailboxIdOrConvId: String, limit: Int) -> List<AmpConversationTurn>)? = null,
    val messageSender: (suspend (recipient: ContactDescriptor, text: String, ghostwrite: Boolean) -> Boolean)? = null,
) {

    private val tools: List<AmpToolDefinition> = buildDefaultTools() + customTools

    /**
     * Processes an incoming message through the Aidos Agent Runner,
     * executing local model reasoning and tool calling.
     */
    suspend fun processTurn(
        incomingMessage: String,
        conversationHistory: List<AmpConversationTurn> = emptyList(),
        baseUrl: String = "http://localhost:11434",
        model: String = "llama3",
        apiKey: String = "",
    ): Result<AmpAgentTurnResult> {
        val adapter = customAdapter ?: OpenAiHttpModelAdapter(
            baseUrl = baseUrl,
            modelId = model,
            apiKey = apiKey,
        )

        val runner = AmpAgentRunner(adapter = adapter, maxSteps = 5)

        return runner.executeTurn(
            systemPrompt = systemPrompt,
            conversationHistory = conversationHistory,
            incomingMessage = incomingMessage,
            tools = tools,
        )
    }

    /**
     * Same as [processTurn], but the loop and the model run in the desktop Aidos app (`/v1/agent`).
     * Returns null when Aidos is not running or does not offer the agent API, so callers can fall back
     * to the embedded runner.
     */
    suspend fun processTurnViaAidos(
        incomingMessage: String,
        model: String,
        conversationHistory: List<AmpConversationTurn> = emptyList(),
        endpoint: AidosEndpoint? = AidosEndpoint.discover(),
    ): Result<AmpAgentTurnResult>? {
        if (endpoint == null) return null
        return AidosDesktopAgent(endpoint).executeTurn(model, systemPrompt, incomingMessage, conversationHistory, tools)
    }

    /**
     * The entry point UIs should call. Aidos on this desktop is the default agent runtime
     * ([useAidosDesktop]); a user who picks their own OpenAI-compatible endpoint sets it to false.
     *
     * If Aidos is selected but not running, the turn falls back to the configured endpoint (which is
     * the user's own choice, local Ollama by default). If Aidos IS running and the turn fails there,
     * that failure is returned as is: silently re-sending the conversation elsewhere would defeat
     * the point of choosing the local runtime.
     */
    suspend fun processTurnAuto(
        incomingMessage: String,
        conversationHistory: List<AmpConversationTurn> = emptyList(),
        useAidosDesktop: Boolean = true,
        aidosModel: String = "",
        baseUrl: String,
        model: String,
        apiKey: String = "",
        endpoint: AidosEndpoint? = if (useAidosDesktop) AidosEndpoint.discover() else null,
    ): Result<AmpAgentTurnResult> {
        if (endpoint == null) return processTurn(incomingMessage, conversationHistory, baseUrl, model, apiKey)
        val chosen = aidosModel.ifBlank { endpoint.listModels().firstOrNull() }
            ?: return Result.failure(IllegalStateException("Aidos is running but has no installed model; install one in the Aidos app"))
        return processTurnViaAidos(incomingMessage, chosen, conversationHistory, endpoint)!!
    }

    private fun buildDefaultTools(): List<AmpToolDefinition> = listOf(
        AmpToolDefinition(
            name = "check_calendar",
            description = "Checks the user's local calendar schedule for conflicts or free slots.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("time") {
                        put("type", "string")
                        put("description", "Time slot to check, e.g. '17:00' or '5:00 PM'")
                    }
                    putJsonObject("date") {
                        put("type", "string")
                        put("description", "Date to check, e.g. 'today' or '2026-10-23'")
                    }
                }
                put("required", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("time"))
                })
            },
            handler = { args ->
                val time = args["time"]?.jsonPrimitive?.content ?: "5:00 PM"
                if (time.contains("5", ignoreCase = true) || time.contains("17", ignoreCase = true)) {
                    AmpToolExecutionResult("Calendar check: 5:00 PM is completely free. User has dinner at 6:30 PM.")
                } else {
                    AmpToolExecutionResult("Calendar check: $time is available with no conflicting appointments.")
                }
            }
        ),

        AmpToolDefinition(
            name = "get_contact_status",
            description = "Retrieves presence, verified ETA, and disclosed reasons for delay for a contact.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("contactName") {
                        put("type", "string")
                        put("description", "Name or handle of contact (e.g. 'Alice', 'Bob')")
                    }
                }
                put("required", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("contactName"))
                })
            },
            handler = { args ->
                val contact = args["contactName"]?.jsonPrimitive?.content ?: "Alice"
                AmpToolExecutionResult(
                    "Progressive Disclosure Record for $contact: Status=LATE, Cause=CAR_BREAKDOWN, Approved ETA=~35 minutes (Highway 101 traffic)."
                )
            }
        ),

        // User-Level Orchestrator Tool: Query messages in another chat
        AmpToolDefinition(
            name = "query_chat_messages",
            description = "Queries recent messages from a specific conversation thread or contact.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("contactName") {
                        put("type", "string")
                        put("description", "Display name or handle of contact (e.g. 'Bob', 'Alice', 'did:aidos:bob')")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max number of recent turns to retrieve (default 5)")
                    }
                }
                put("required", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("contactName"))
                })
            },
            handler = { args ->
                val contactName = args["contactName"]?.jsonPrimitive?.content ?: "Bob"
                val limit = args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5

                val resolvedContact = contactLookup?.invoke(contactName)
                if (resolvedContact != null && messageFetcher != null) {
                    val messages = messageFetcher.invoke(resolvedContact.mailboxId, limit)
                    if (messages.isEmpty()) {
                        AmpToolExecutionResult("No recent messages found with ${resolvedContact.displayName}.")
                    } else {
                        val formatted = messages.joinToString("\n") { turn ->
                            val role = if (turn.role.equals("assistant", true)) "Aidos" else (turn.senderId ?: "User")
                            "- [$role]: ${turn.text}"
                        }
                        AmpToolExecutionResult("Recent messages in chat with ${resolvedContact.displayName}:\n$formatted")
                    }
                } else {
                    // Simulated fallback
                    AmpToolExecutionResult("Recent messages in chat with $contactName:\n- [Peer]: \"Hey, can we sync at 5:00 PM?\"\n- [User]: \"Let me check my calendar.\"")
                }
            }
        ),

        // User-Level Orchestrator Tool: Send message to a contact (Dual Mode: Agent vs Ghostwriter)
        AmpToolDefinition(
            name = "send_chat_message",
            description = "Sends an outbound direct message to a specified contact. Default is sending as your autonomous AI agent delegate (badged as 'Delegated Runner'). Set ghostwrite=true if the user explicitly requested to send the message as themselves / directly from them (badged as 'Human Direct'). Never send as sealed message.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("recipientName") {
                        put("type", "string")
                        put("description", "Display name or handle of the recipient (e.g. 'Bob', 'Alice')")
                    }
                    putJsonObject("messageText") {
                        put("type", "string")
                        put("description", "The exact message text to dispatch")
                    }
                    putJsonObject("ghostwrite") {
                        put("type", "boolean")
                        put("description", "Optional: set to true to send as the human user directly ('send as me' / ghostwritten). Default is false (send as agent delegate).")
                    }
                }
                put("required", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("recipientName"))
                    add(kotlinx.serialization.json.JsonPrimitive("messageText"))
                })
            },
            handler = { args ->
                val recipientName = args["recipientName"]?.jsonPrimitive?.content ?: "Bob"
                val text = args["messageText"]?.jsonPrimitive?.content ?: ""
                val ghostwrite = args["ghostwrite"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

                if (text.isBlank()) {
                    AmpToolExecutionResult("Cannot send empty message.", isError = true)
                } else {
                    val resolved = contactLookup?.invoke(recipientName)
                    val modeLabel = if (ghostwrite) "Ghostwriter / Human Direct" else "Agent Delegate"
                    if (resolved != null && messageSender != null) {
                        val ok = messageSender.invoke(resolved, text, ghostwrite)
                        if (ok) {
                            AmpToolExecutionResult("Successfully dispatched message ($modeLabel) to ${resolved.displayName} (${resolved.principalId}): \"$text\"")
                        } else {
                            AmpToolExecutionResult("Failed to dispatch message to ${resolved.displayName}.", isError = true)
                        }
                    } else {
                        AmpToolExecutionResult("Simulated dispatch ($modeLabel): Message sent to $recipientName: \"$text\"")
                    }
                }
            }
        )
    )

    companion object {
        const val DEFAULT_AMP_SYSTEM_PROMPT = """You are Aidos, an intelligent privacy-preserving personal AI orchestrator operating inside the Agentic Messenger Platform (AMP).
You collaborate in an end-to-end encrypted 4-party conversation mesh and act on behalf of the user.
Rules:
1. Be helpful, concise, and direct.
2. If the user asks about schedules, availability, or meetings, ALWAYS call the 'check_calendar' tool.
3. If the user asks about contact status, reasons for delay, or ETA, ALWAYS call the 'get_contact_status' tool.
4. If the user asks what was said in a chat or asks for messages from a contact, ALWAYS call 'query_chat_messages'.
5. If the user asks to send a message to someone or tell someone something, ALWAYS call 'send_chat_message'.
6. When sending a message via 'send_chat_message', DEFAULT to ghostwrite=false (sending as Agent Delegate). Only set ghostwrite=true if the user specifically instructs you to send it as themselves, from me, directly, or without disclosing AI involvement. Never create sealed/human-only messages.
7. When answering after tool calls, summarize the result clearly."""
    }
}
