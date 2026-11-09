package com.agenticmessenger.agent

import dev.aidos.amp.AmpToolDefinition
import dev.aidos.amp.AmpToolExecutionResult
import dev.aidos.amp.HttpTransport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AidosDesktopAgentTest {
    private val endpoint = AidosEndpoint("http://127.0.0.1:18080", "tok")
    private val lookup = AmpToolDefinition("lookup", "find", JsonObject(emptyMap())) { AmpToolExecutionResult("found: ${it["q"]?.jsonPrimitive?.content}") }

    @Test
    fun runsToolsLocallyAndPostsResultsBack() = runBlocking {
        val seen = mutableListOf<Pair<String, String>>()
        val transport = HttpTransport { url, headers, body ->
            seen += url to body
            assertEquals("Bearer tok", headers["Authorization"])
            if (url.endsWith("/v1/agent/turns")) {
                val req = Json.parseToJsonElement(body).jsonObject
                assertEquals("m", req["model"]!!.jsonPrimitive.content)
                assertEquals("lookup", req["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
                """{"status":"tool_calls","runId":"r1","toolCalls":[{"callId":"call_1","name":"lookup","arguments":{"q":"anna"}}]}"""
            } else {
                assertTrue(url.endsWith("/v1/agent/runs/r1/tool-results"))
                val r = Json.parseToJsonElement(body).jsonObject["results"]!!.jsonArray.single().jsonObject
                assertEquals("found: anna", r["output"]!!.jsonPrimitive.content)
                """{"status":"completed","runId":"r1","reply":"Anna is free","steps":2,"executedToolCalls":[{"toolName":"lookup","argumentsJson":"{}","resultText":"found: anna"}]}"""
            }
        }
        val result = AidosDesktopAgent(endpoint, transport).executeTurn("m", "sys", "hi", emptyList(), listOf(lookup)).getOrThrow()
        assertEquals("Anna is free", result.replyText)
        assertEquals(2, result.totalSteps)
        assertEquals("lookup", result.executedToolCalls.single().toolName)
        assertEquals(2, seen.size)
    }

    @Test
    fun unknownToolAndThrowingToolBecomeErrorResults() = runBlocking {
        val boom = AmpToolDefinition("boom", "", JsonObject(emptyMap())) { error("kaput") }
        val outputs = mutableListOf<String>()
        val transport = HttpTransport { url, _, body ->
            if (url.endsWith("/turns")) {
                """{"status":"tool_calls","runId":"r","toolCalls":[{"callId":"a","name":"boom","arguments":{}}]}"""
            } else {
                val r = Json.parseToJsonElement(body).jsonObject["results"]!!.jsonArray.single().jsonObject
                outputs += r["output"]!!.jsonPrimitive.content + "|" + r["isError"]!!.jsonPrimitive.content
                if (outputs.size == 1) """{"status":"tool_calls","runId":"r","toolCalls":[{"callId":"b","name":"missing","arguments":{}}]}"""
                else """{"status":"completed","reply":"done"}"""
            }
        }
        AidosDesktopAgent(endpoint, transport).executeTurn("m", "s", "x", emptyList(), listOf(boom)).getOrThrow()
        assertEquals(listOf("Tool 'boom' threw error: kaput|true", "Tool 'missing' not found in registry|true"), outputs)
    }

    @Test
    fun failedRunAndApiErrorsAreFailures() = runBlocking {
        val failed = HttpTransport { _, _, _ -> """{"status":"failed","error":"model not installed"}""" }
        val r1 = AidosDesktopAgent(endpoint, failed).executeTurn("m", "s", "x", emptyList(), emptyList())
        assertTrue(r1.exceptionOrNull()!!.message!!.contains("model not installed"))
        val api = HttpTransport { _, _, _ -> """{"error":{"message":"agent runtime is busy","type":"invalid_request_error"}}""" }
        val r2 = AidosDesktopAgent(endpoint, api).executeTurn("m", "s", "x", emptyList(), emptyList())
        assertTrue(r2.exceptionOrNull()!!.message!!.contains("busy"))
    }

    @Test
    fun runawayToolLoopIsStopped() = runBlocking {
        val loop = HttpTransport { _, _, _ -> """{"status":"tool_calls","runId":"r","toolCalls":[{"callId":"a","name":"lookup","arguments":{}}]}""" }
        val r = AidosDesktopAgent(endpoint, loop, maxToolRounds = 3).executeTurn("m", "s", "x", emptyList(), listOf(lookup))
        assertTrue(r.exceptionOrNull()!!.message!!.contains("more than 3"))
    }

    @Test
    fun discoveryNeedsReadyStatusAndAgentApi() {
        fun file(text: String) = Files.createTempFile("engine", ".json").toFile().also { it.writeText(text); it.deleteOnExit() }
        val ok = file("""{"status":"READY","baseUrl":"http://127.0.0.1:9/","token":"t","agentApi":"/v1/agent"}""")
        assertEquals(AidosEndpoint("http://127.0.0.1:9", "t"), AidosEndpoint.discover(ok))
        assertNull(AidosEndpoint.discover(file("""{"status":"READY","baseUrl":"http://x","token":"t"}""")), "older Aidos without agent API")
        assertNull(AidosEndpoint.discover(file("""{"status":"STARTING","baseUrl":"http://x","token":"t","agentApi":"/v1/agent"}""")))
        assertNull(AidosEndpoint.discover(java.io.File("/nonexistent/engine.json")))
        assertNull(AidosEndpoint.discover(file("not json")))
    }

    @Test
    fun serviceFallsBackWhenAidosIsAbsent() = runBlocking {
        assertNull(AmpAgentService().processTurnViaAidos("hi", "m", endpoint = null))
        assertNotNull(AmpAgentService().processTurnViaAidos("hi", "m", endpoint = AidosEndpoint("http://127.0.0.1:1", "t")))
    }
}
