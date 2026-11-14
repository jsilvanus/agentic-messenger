package com.agenticmessenger.mcp

import com.agenticmessenger.agent.AmpAgentService
import com.agenticmessenger.agent.ContactDescriptor
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmpMcpServerTest {
    private fun <T> connected(mcp: AmpMcpServer, block: suspend (Client) -> T): T = runBlocking {
        val pair = ChannelTransport.createLinkedPair()
        val client = Client(Implementation("test-client", "1"))
        mcp.server.createSession(pair.serverTransport)
        client.connect(pair.clientTransport)
        try {
            block(client)
        } finally {
            client.close()
        }
    }

    @Test
    fun publishesOnlyTheChatToolsByDefault() {
        val service = AmpAgentService()
        val mcp = AmpMcpServer(service.toolDefinitions)
        assertEquals(setOf("get_contact_status", "query_chat_messages", "send_chat_message"), mcp.toolNames.toSet())
        val listed = connected(mcp) { it.listTools().tools.map { t -> t.name } }
        assertEquals(mcp.toolNames.toSet(), listed.toSet())
        assertFalse("check_calendar" in listed, "the calendar is not published")
    }

    @Test
    fun aToolCallRunsTheAmpHandler() {
        val mcp = AmpMcpServer(AmpAgentService().toolDefinitions)
        val result = connected(mcp) {
            it.callTool("get_contact_status", buildJsonObject { put("contactName", "Bob") })
        }
        assertFalse(result.isError == true)
        assertTrue((result.content.single() as TextContent).text.isNotBlank())
    }

    private fun sendOver(mcp: AmpMcpServer, ghostwrite: Boolean?) = connected(mcp) {
        it.callTool("send_chat_message", buildJsonObject {
            put("recipientName", "Bob"); put("messageText", "hi")
            ghostwrite?.let { g -> put("ghostwrite", g) }
        })
    }

    private fun serviceRecordingSends(sent: MutableList<Boolean>) = AmpAgentService(
        contactLookup = { ContactDescriptor("bob", "Bob", "mbx", "did:aidos:bob") },
        messageSender = { _, _, ghostwrite -> sent += ghostwrite; true },
    )

    @Test
    fun sendsAsTheAgentByDefaultAndAsTheHumanOnRequest() {
        val sent = mutableListOf<Boolean>()
        val mcp = AmpMcpServer(serviceRecordingSends(sent).toolDefinitions)
        sendOver(mcp, null)
        sendOver(mcp, false)
        sendOver(mcp, true)
        assertEquals(listOf(false, false, true), sent, "default and ghostwrite=false are the agent; true is the human")
    }

    @Test
    fun aDeploymentCanRemoveSendingAsTheHuman() {
        val sent = mutableListOf<Boolean>()
        val mcp = AmpMcpServer(serviceRecordingSends(sent).toolDefinitions, allowSendAsHuman = false)
        val schema = connected(mcp) { it.listTools().tools.single { t -> t.name == "send_chat_message" }.inputSchema }
        assertFalse("ghostwrite" in (schema.properties?.keys ?: emptySet()), "not advertised")
        sendOver(mcp, true)
        assertEquals(listOf(false), sent, "ghostwrite=true is ignored")
    }

    @Test
    fun nestedPropertiesInArgumentsCannotBypassAllowSendAsHumanFalse() {
        val sent = mutableListOf<Boolean>()
        val mcp = AmpMcpServer(serviceRecordingSends(sent).toolDefinitions, allowSendAsHuman = false)
        connected(mcp) {
            it.callTool("send_chat_message", buildJsonObject {
                put("recipientName", "Bob")
                put("messageText", "hi")
                put("ghostwrite", true)
                put("properties", buildJsonObject { put("ghostwrite", true) })
            })
        }
        assertEquals(listOf(false), sent, "nested properties must never defeat ghostwrite stripping")
    }

    @Test
    fun anUnlistedToolCannotBeCalled() {
        val mcp = AmpMcpServer(AmpAgentService().toolDefinitions)
        val outcome = connected(mcp) {
            runCatching { it.callTool("check_calendar", buildJsonObject { put("time", "5:00 PM") }) }
        }
        // Either a protocol error or an error result; never a delivered message.
        assertTrue(outcome.isFailure || outcome.getOrNull()?.isError == true)
    }
}
