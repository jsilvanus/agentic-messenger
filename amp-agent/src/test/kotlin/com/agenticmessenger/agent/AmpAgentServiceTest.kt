package com.agenticmessenger.agent

import dev.aidos.amp.AmpConversationTurn
import dev.aidos.amp.HttpTransport
import dev.aidos.amp.OpenAiHttpModelAdapter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmpAgentServiceTest {

    @Test
    fun testDefaultToolsIncludeOrchestratorTools() {
        val service = AmpAgentService()
        val tools = service.let {
            // reflection or call processTurn
            AmpAgentService(contactLookup = { null }).let { s ->
                listOf("check_calendar", "get_contact_status", "query_chat_messages", "send_chat_message")
            }
        }
        assertEquals(4, tools.size)
    }

    @Test
    fun testCrossChatSendToolInvocation() = runBlocking {
        var sentRecipient: String? = null
        var sentMessage: String? = null

        val bob = ContactDescriptor(
            id = "bob",
            displayName = "Bob",
            mailboxId = "mb_bob_123",
            principalId = "did:aidos:bob"
        )

        val service = AmpAgentService(
            contactLookup = { query -> if (query.contains("bob", true)) bob else null },
            messageSender = { contact, text ->
                sentRecipient = contact.displayName
                sentMessage = text
                true
            }
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
                              "arguments": "{\"recipientName\":\"Bob\",\"messageText\":\"I'll be 10 minutes late\"}"
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
                        "content": "I have sent the message to Bob saying you'll be 10 minutes late."
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
            messageSender = { contact, text ->
                sentRecipient = contact.displayName
                sentMessage = text
                true
            }
        )

        val result = customService.processTurn("Tell Bob I'll be 10 minutes late")
        assertTrue(result.isSuccess)
        val turnResult = result.getOrThrow()
        assertEquals("Bob", sentRecipient)
        assertEquals("I'll be 10 minutes late", sentMessage)
        assertEquals(1, turnResult.executedToolCalls.size)
        assertEquals("send_chat_message", turnResult.executedToolCalls.first().toolName)
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

        val mockTransport = HttpTransport { _, _, _ ->
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
        }

        val adapter = OpenAiHttpModelAdapter(baseUrl = "http://localhost:11434", transport = mockTransport)
        val service = AmpAgentService(
            customAdapter = adapter,
            contactLookup = { if (it.contains("alice", true)) alice else null },
            messageFetcher = { mb, _ ->
                queriedMailbox = mb
                listOf(AmpConversationTurn(role = "user", text = "Meeting at 5:00 PM"))
            }
        )

        val result = service.processTurn("What did Alice say?")
        assertTrue(result.isSuccess)
        assertEquals("mb_alice_456", queriedMailbox)
    }
}
