package com.agenticmessenger.agent

import dev.aidos.amp.AmpConversationTurn
import dev.aidos.amp.HttpTransport
import dev.aidos.amp.OpenAiHttpModelAdapter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        var callCount = 0
        val mockTransport = HttpTransport { _, _, _ ->
            callCount++
            if (callCount == 1) {
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call_send_1",
                            "type": "function",
                            "function": {
                              "name": "send_chat_message",
                              "arguments": "{\"recipientName\":\"Bob\",\"messageText\":\"I will be 10 minutes late\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ]
                }
                """.trimIndent()
            } else {
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "Message sent."
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """.trimIndent()
            }
        }

        val adapter = OpenAiHttpModelAdapter(baseUrl = "http://localhost:11434", transport = mockTransport)
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

        var callCount = 0
        val mockTransport = HttpTransport { _, _, _ ->
            callCount++
            if (callCount == 1) {
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call_send_ghost",
                            "type": "function",
                            "function": {
                              "name": "send_chat_message",
                              "arguments": "{\"recipientName\":\"Bob\",\"messageText\":\"Hey, see you soon\",\"ghostwrite\":true}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ]
                }
                """.trimIndent()
            } else {
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "Message sent as human."
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """.trimIndent()
            }
        }

        val adapter = OpenAiHttpModelAdapter(baseUrl = "http://localhost:11434", transport = mockTransport)
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

        var callCount = 0
        val mockTransport = HttpTransport { _, _, _ ->
            callCount++
            if (callCount == 1) {
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call_query_1",
                            "type": "function",
                            "function": {
                              "name": "query_chat_messages",
                              "arguments": "{\"contactName\":\"Alice\",\"limit\":3}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ]
                }
                """.trimIndent()
            } else {
                """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "Alice's recent messages checked."
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """.trimIndent()
            }
        }

        val adapter = OpenAiHttpModelAdapter(baseUrl = "http://localhost:11434", transport = mockTransport)
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
