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

    /** A real HTTP server standing in for the desktop Aidos app, so the default transport runs too. */
    private class FakeAidos(val models: String = """{"data":[{"id":"qwen-small"},{"id":"other"}]}""") : AutoCloseable {
        val requests = mutableListOf<String>()
        private val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0).apply {
            fun reply(ex: com.sun.net.httpserver.HttpExchange, body: String) {
                ex.responseHeaders.add("Content-Type", "application/json")
                val bytes = body.toByteArray(); ex.sendResponseHeaders(200, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
            }
            createContext("/v1/models") { ex -> requests += "GET models auth=${ex.requestHeaders.getFirst("Authorization")}"; reply(ex, models) }
            createContext("/v1/agent/turns") { ex ->
                val req = Json.parseToJsonElement(ex.requestBody.readBytes().decodeToString()).jsonObject
                requests += "turn model=${req["model"]!!.jsonPrimitive.content}"
                reply(ex, """{"status":"completed","reply":"from aidos","steps":1}""")
            }
            start()
        }
        val endpoint = AidosEndpoint("http://127.0.0.1:${server.address.port}", "tok")
        override fun close() = server.stop(0)
    }

    @Test
    fun autoUsesAidosWithItsFirstModelAndTheToken() = runBlocking {
        FakeAidos().use { aidos ->
            val r = AmpAgentService().processTurnAuto("hi", baseUrl = "http://127.0.0.1:1", model = "ignored", endpoint = aidos.endpoint).getOrThrow()
            assertEquals("from aidos", r.replyText)
            assertEquals(listOf("GET models auth=Bearer tok", "turn model=qwen-small"), aidos.requests)
        }
    }

    @Test
    fun autoHonoursAnExplicitAidosModelAndNeverCallsModelsThen() = runBlocking {
        FakeAidos().use { aidos ->
            AmpAgentService().processTurnAuto("hi", aidosModel = "other", baseUrl = "x", model = "y", endpoint = aidos.endpoint).getOrThrow()
            assertEquals(listOf("turn model=other"), aidos.requests)
        }
    }

    @Test
    fun autoFailsClearlyWhenAidosHasNoModelAndDoesNotLeakToTheOtherEndpoint() = runBlocking {
        FakeAidos(models = """{"data":[]}""").use { aidos ->
            val r = AmpAgentService().processTurnAuto("hi", baseUrl = "http://127.0.0.1:1", model = "y", endpoint = aidos.endpoint)
            assertTrue(r.exceptionOrNull()!!.message!!.contains("no installed model"))
            assertEquals(listOf("GET models auth=Bearer tok"), aidos.requests)
        }
    }

    @Test
    fun autoWithAidosNotRunningUsesTheConfiguredEndpoint() = runBlocking {
        var hit: String? = null
        val svc = AmpAgentService(customAdapter = object : dev.aidos.kernel.ModelAdapter {
            override val providerId = "t"; override val modelId = "m"; override val modelVersion = "1"
            override val contextWindow = 1024; override val isLocal = true
            override fun supportsNativeToolCalls() = false
            override suspend fun invoke(request: dev.aidos.kernel.ModelRequest): Result<dev.aidos.kernel.ModelResponse> {
                hit = "adapter"
                return Result.success(dev.aidos.kernel.ModelResponse(listOf(dev.aidos.kernel.TextOutput("plain")), dev.aidos.kernel.StopReason.END_TURN, null, null))
            }
        })
        val r = svc.processTurnAuto("hi", baseUrl = "x", model = "y", endpoint = null).getOrThrow()
        assertEquals("plain", r.replyText)
        assertEquals("adapter", hit)
        // opting out of Aidos never even looks for it
        assertEquals("plain", svc.processTurnAuto("hi", useAidosDesktop = false, baseUrl = "x", model = "y", endpoint = null).getOrThrow().replyText)
    }
}
