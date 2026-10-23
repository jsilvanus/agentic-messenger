package com.agenticmessenger.ui.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiConfigTest {

    @Test
    fun testDefaultOllamaConfiguration() {
        val config = OpenAiConfig()
        assertEquals("localhost", config.host)
        assertEquals(11434, config.port)
        assertEquals("llama3", config.model)
        assertEquals("http://localhost:11434", config.baseUrl)
        assertEquals("http://localhost:11434/v1/chat/completions", config.chatCompletionsUrl)
        assertEquals("http://localhost:11434/v1/models", config.modelsUrl)
        assertTrue(config.isEnabled)
    }

    @Test
    fun testCustomAidosHostAndPortNormalization() {
        val config = OpenAiConfig(
            host = "http://192.168.1.50/",
            port = 8000,
            model = "aidos-mesh"
        )
        assertEquals("http://192.168.1.50", config.normalizedHost)
        assertEquals("http://192.168.1.50:8000", config.baseUrl)
        assertEquals("http://192.168.1.50:8000/v1/chat/completions", config.chatCompletionsUrl)
        assertEquals("http://192.168.1.50:8000/v1/models", config.modelsUrl)
    }
}
