package com.agenticmessenger.ui.ai

import com.agenticmessenger.agent.AidosEndpoint
import com.agenticmessenger.agent.AmpAgentService
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.assertTrue
import dev.aidos.kernel.ModelAdapter
import dev.aidos.kernel.ModelRequest
import dev.aidos.kernel.ModelResponse
import dev.aidos.kernel.StopReason
import dev.aidos.kernel.TextOutput
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantTurnsTest {
    private class Direct : ModelAdapter {
        var calls = 0
        override val providerId = "direct"
        override val modelId = "m"
        override val modelVersion = "1"
        override val contextWindow = 4096
        override val isLocal = true
        override fun supportsNativeToolCalls() = false
        override suspend fun invoke(request: ModelRequest): Result<ModelResponse> {
            calls++
            return Result.success(ModelResponse(listOf(TextOutput("direct reply")), StopReason.END_TURN, null, null))
        }
    }

    @Test
    fun directSelectionUsesTheOpenAiAdapter() = runBlocking {
        val adapter = Direct()
        val service = AmpAgentService(customAdapter = adapter)
        val result = service.runAssistantTurn(OpenAiConfig(useDirectOpenAi = true), "hi").getOrThrow()
        assertEquals("direct reply", result.replyText)
        assertEquals(1, adapter.calls)
    }

    @Test
    fun defaultIsAidosAndNeverTouchesTheDirectAdapter() = runBlocking {
        val adapter = Direct()
        val service = AmpAgentService(customAdapter = adapter)
        // No Aidos running (no endpoint discovered in tests): the turn fails instead of falling back to the direct client.
        service.runAssistantTurn(OpenAiConfig(), "hi")
        assertEquals(0, adapter.calls)
    }

    /** A local server standing in for desktop Aidos: `/v1/models` and `/v1/agent/turns`. */
    private fun fakeAidos(models: String, onTurn: (String) -> Unit = {}, block: (AidosEndpoint) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models") { ex ->
            val ok = ex.requestHeaders.getFirst("Authorization") == "Bearer tok"
            val body = if (ok) models else """{"error":{"message":"unauthorized"}}"""
            ex.sendResponseHeaders(if (ok) 200 else 401, body.length.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        server.createContext("/v1/agent/turns") { ex ->
            onTurn(ex.requestBody.readBytes().decodeToString())
            val body = """{"status":"completed","runId":"r","reply":"hello from aidos","steps":1}"""
            ex.sendResponseHeaders(200, body.length.toLong())
            ex.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        try {
            block(AidosEndpoint("http://127.0.0.1:${server.address.port}", "tok"))
        } finally {
            server.stop(0)
        }
    }

    private val models = """{"data":[
        {"id":"embed-x","kind":"embedding","installed":true},
        {"id":"catalog-only","kind":"chat","installed":false},
        {"id":"llama-local","kind":"chat","installed":true}]}"""

    @Test
    fun blankModelUsesTheFirstInstalledChatModelOverRealHttp() {
        var sent = ""
        fakeAidos(models, onTurn = { sent = it }) { endpoint ->
            val result = runBlocking {
                AmpAgentService().runAssistantTurn(OpenAiConfig(), "hi", endpoint = endpoint)
            }.getOrThrow()
            assertEquals("hello from aidos", result.replyText)
            assertTrue(""""model":"llama-local"""" in sent, sent)
            // Locality is left out so Aidos applies its own default (local_only).
            assertTrue("locality" !in sent, sent)
        }
    }

    @Test
    fun explicitModelIsUsedAsIs() {
        var sent = ""
        fakeAidos(models, onTurn = { sent = it }) { endpoint ->
            runBlocking {
                AmpAgentService().runAssistantTurn(OpenAiConfig(aidosModel = "ollama:qwen"), "hi", endpoint = endpoint)
            }.getOrThrow()
            assertTrue(""""model":"ollama:qwen"""" in sent, sent)
        }
    }

    @Test
    fun noInstalledChatModelIsAClearError() {
        fakeAidos("""{"data":[{"id":"embed-x","kind":"embedding","installed":true}]}""") { endpoint ->
            val failure = runBlocking {
                AmpAgentService().runAssistantTurn(OpenAiConfig(), "hi", endpoint = endpoint)
            }.exceptionOrNull()
            assertTrue(failure?.message?.contains("no chat model installed") == true, failure?.message)
        }
    }

    @Test
    fun rejectedTokenSurfacesAsAnError() {
        fakeAidos(models) { endpoint ->
            val failure = runBlocking {
                AmpAgentService().runAssistantTurn(OpenAiConfig(), "hi", endpoint = endpoint.copy(token = "wrong"))
            }.exceptionOrNull()
            assertTrue(failure?.message?.contains("401") == true, failure?.message)
        }
    }
}
