package com.agenticmessenger.ui.ai

import com.agenticmessenger.agent.AmpAgentService
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
}
