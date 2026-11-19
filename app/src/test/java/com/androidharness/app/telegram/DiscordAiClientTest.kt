package com.androidharness.app.telegram

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DiscordAiClientTest {
    @Test
    fun normalizesOpenAiCompatibleBaseUrl() {
        val endpoint = DiscordAiEndpointValidator.validate(" https://api.example.test/v1/ ", "model:v2")

        assertEquals("https://api.example.test/v1", endpoint.baseUrl)
        assertEquals("https://api.example.test/v1/chat/completions", endpoint.chatCompletionsUrl)
        assertEquals("model:v2", endpoint.model)
    }

    @Test
    fun rejectsRemotePlainHttpAndCredentialBearingUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscordAiEndpointValidator.validate("http://api.example.test/v1", "model")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiscordAiEndpointValidator.validate("https://user:pass@api.example.test/v1", "model")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiscordAiEndpointValidator.validate("https://api.example.test/v1?key=secret", "model")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiscordAiEndpointValidator.validate("https://api.example.test/v1", "bad model")
        }
    }

    @Test
    fun allowsLoopbackHttpForLocalModelServers() {
        assertEquals(
            "http://10.0.2.2:8080/v1/chat/completions",
            DiscordAiEndpointValidator.validate("http://10.0.2.2:8080/v1", "local-model").chatCompletionsUrl,
        )
    }

    @Test
    fun legacyConfigGetsDefaultsAndStringRedactsBothCredentials() {
        val legacy = Json { ignoreUnknownKeys = true }.decodeFromString<DiscordBotConfig>("""{"token":"discord-token"}""")
        assertEquals(DiscordAiEndpointValidator.DEFAULT_BASE_URL, legacy.aiBaseUrl)
        assertEquals(DiscordAiEndpointValidator.DEFAULT_MODEL, legacy.aiModel)

        val current = legacy.copy(aiApiKey = "private-ai-key")
        assertFalse(current.toString().contains("discord-token"))
        assertFalse(current.toString().contains("private-ai-key"))
    }
}
