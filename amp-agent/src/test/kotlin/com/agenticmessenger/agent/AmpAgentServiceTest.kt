package com.agenticmessenger.agent

import dev.aidos.amp.AmpConversationTurn
import dev.aidos.kernel.ModelAdapter
import dev.aidos.kernel.ModelRef
import dev.aidos.kernel.ModelRequest
import dev.aidos.kernel.ModelResponse
import dev.aidos.kernel.StopReason
import dev.aidos.kernel.TextOutput
import dev.aidos.kernel.ToolCall
import dev.aidos.kernel.ToolCallOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A model that answers with a fixed script, one response per call: what the removed HTTP adapter's mock transport did. */
private class ScriptedAdapter(private val script: List<ModelResponse>) : ModelAdapter {
    override val providerId = "test"
    override val modelId = "scripted"
    override val modelVersion = "1"
    override val contextWindow = 4096
    override val isLocal = true
    override fun supportsNativeToolCalls() = true
    private var next = 0

    override suspend fun invoke(request: ModelRequest): Result<ModelResponse> =
        Result.success(script[minOf(next++, script.lastIndex)])
}

private fun toolCall(id: String, name: String, argumentsJson: String) = ModelResponse(
    outputs = listOf(
        ToolCallOutput(
            ToolCall(
                callId = id,
                toolName = name,
                arguments = Json.parseToJsonElement(argumentsJson).jsonObject,
                capabilityId = null,
                rawText = argumentsJson,
            )
        )
    ),
    stopReason = StopReason.TOOL_USE,
    usage = null,
    model = ModelRef("scripted", "1"),
)

private fun reply(text: String) = ModelResponse(
    outputs = listOf(TextOutput(text)),
    stopReason = StopReason.END_TURN,
    usage = null,
    model = ModelRef("scripted", "1"),
)

class AmpAgentServiceTest {

    @Test
    fun testDefaultToolsIncludeOrchestratorTools() {
        val service = AmpAgentService()
        val tools = service.let {
            listOf("check_calendar", "get_contact_status", "query_chat_messages", "send_chat_message")
        }
        assertEquals(4, tools.size)
    }

    @Test
    fun testCrossChatSendToolInvocationDefaultAgent() = runBlocking {
        var sentRecipient: String? = null
        var sentMessage: String? = null
        var sentGhostwrite: Boolean? = null

        val bob = ContactDescriptor(
            id = "bob",
            displayName = "Bob",
            mailboxId = "mb_bob_123",
            principalId = "did:aidos:bob"
        )

        val adapter = ScriptedAdapter(listOf(
            toolCall("call_send_1", "send_chat_message", """{"recipientName":"Bob","messageText":"I will be 10 minutes late"}"""),
            reply("Message sent."),
        ))
        val customService = AmpAgentService(
            customAdapter = adapter,
            contactLookup = { if (it.contains("bob", true)) bob else null },
            messageSender = { contact, text, ghostwrite ->
                sentRecipient = contact.displayName
                sentMessage = text
                sentGhostwrite = ghostwrite
                true
            }
        )

        val result = customService.processTurn("Tell Bob I will be 10 minutes late")
        assertTrue(result.isSuccess)
        val turnResult = result.getOrThrow()
        assertEquals("Bob", sentRecipient)
        assertEquals("I will be 10 minutes late", sentMessage)
        assertFalse(sentGhostwrite ?: true, "Default mode must be send as agent delegate (ghostwrite=false)")
        assertEquals(1, turnResult.executedToolCalls.size)
    }

    @Test
    fun testCrossChatSendToolInvocationGhostwrite() = runBlocking {
        var sentRecipient: String? = null
        var sentMessage: String? = null
        var sentGhostwrite: Boolean? = null

        val bob = ContactDescriptor(
            id = "bob",
            displayName = "Bob",
            mailboxId = "mb_bob_123",
            principalId = "did:aidos:bob"
        )

        val adapter = ScriptedAdapter(listOf(
            toolCall("call_send_ghost", "send_chat_message", """{"recipientName":"Bob","messageText":"Hey, see you soon","ghostwrite":true}"""),
            reply("Message sent as human."),
        ))
        val customService = AmpAgentService(
            customAdapter = adapter,
            contactLookup = { if (it.contains("bob", true)) bob else null },
            messageSender = { contact, text, ghostwrite ->
                sentRecipient = contact.displayName
                sentMessage = text
                sentGhostwrite = ghostwrite
                true
            }
        )

        val result = customService.processTurn("Send to Bob as me: Hey, see you soon")
        assertTrue(result.isSuccess)
        val turnResult = result.getOrThrow()
        assertEquals("Bob", sentRecipient)
        assertEquals("Hey, see you soon", sentMessage)
        assertTrue(sentGhostwrite ?: false, "Ghostwrite mode must be true when instructed")
        assertEquals(1, turnResult.executedToolCalls.size)
    }

    @Test
    fun testCrossChatQueryToolInvocation() = runBlocking {
        var queriedMailbox: String? = null

        val alice = ContactDescriptor(
            id = "alice",
            displayName = "Alice",
            mailboxId = "mb_alice_456",
            principalId = "did:aidos:alice"
        )

        val adapter = ScriptedAdapter(listOf(
            toolCall("call_query_1", "query_chat_messages", """{"contactName":"Alice","limit":3}"""),
            reply("Alice's recent messages checked."),
        ))
        val customService = AmpAgentService(
            customAdapter = adapter,
            contactLookup = { if (it.contains("alice", true)) alice else null },
            messageFetcher = { mailbox, limit ->
                queriedMailbox = mailbox
                listOf(
                    AmpConversationTurn("user", "Are you available?"),
                    AmpConversationTurn("assistant", "Yes, free at 5 PM")
                )
            }
        )

        val result = customService.processTurn("What did Alice say in our chat?")
        assertTrue(result.isSuccess)
        assertEquals("mb_alice_456", queriedMailbox)
    }
}
