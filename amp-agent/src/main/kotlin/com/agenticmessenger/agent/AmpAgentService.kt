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
 */
class AmpAgentService(
    val systemPrompt: String = DEFAULT_AMP_SYSTEM_PROMPT,
    customTools: List<AmpToolDefinition> = emptyList(),
    val customAdapter: ModelAdapter? = null,
    val contactLookup: ((nameOrQuery: String) -> ContactDescriptor?)? = null,
    val messageFetcher: (suspend (mailboxIdOrConvId: String, limit: Int) -> List<AmpConversationTurn>)? = null,
    val messageSender: (suspend (recipient: ContactDescriptor, text: String) -> Boolean)? = null,
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
            description = "Queries progressive disclosure status, ETA, and breakdown reasons for contacts.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("contactName") {
                        put("type", "string")
                        put("description", "The contact's display name or handle (e.g. 'Jack' or 'did:aidos:jack')")
                    }
                }
                put("required", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("contactName"))
                })
            },
            handler = { args ->
                val contact = args["contactName"]?.jsonPrimitive?.content ?: "Jack"
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

        // User-Level Orchestrator Tool: Send message to a contact
        AmpToolDefinition(
            name = "send_chat_message",
            description = "Sends an outbound direct message to a specified contact or chat.",
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
                }
                put("required", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("recipientName"))
                    add(kotlinx.serialization.json.JsonPrimitive("messageText"))
                })
            },
            handler = { args ->
                val recipientName = args["recipientName"]?.jsonPrimitive?.content ?: "Bob"
                val text = args["messageText"]?.jsonPrimitive?.content ?: ""

                if (text.isBlank()) {
                    AmpToolExecutionResult("Cannot send empty message.", isError = true)
                } else {
                    val resolved = contactLookup?.invoke(recipientName)
                    if (resolved != null && messageSender != null) {
                        val ok = messageSender.invoke(resolved, text)
                        if (ok) {
                            AmpToolExecutionResult("Successfully dispatched message to ${resolved.displayName} (${resolved.principalId}): \"$text\"")
                        } else {
                            AmpToolExecutionResult("Failed to dispatch message to ${resolved.displayName}.", isError = true)
                        }
                    } else {
                        AmpToolExecutionResult("Simulated dispatch: Message sent to $recipientName: \"$text\"")
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
6. When answering after tool calls, summarize the result clearly."""
    }
}
