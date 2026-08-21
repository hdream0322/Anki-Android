package com.ichi2.anki.ai

import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiProviderTest {
    private val provider = GeminiProvider()

    private fun Request.bodyAsJson(): JSONObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `buildRequest targets streamGenerateContent with key as query param`() {
        val request =
            provider.buildRequest(
                apiKey = "gm-test",
                model = "gemini-2.0-flash",
                systemPrompt = null,
                messages = listOf(AiChatMessage(AiChatRole.USER, "hi")),
            )

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse&key=gm-test",
            request.url.toString(),
        )
    }

    @Test
    fun `buildRequest maps ASSISTANT role to model and includes system_instruction`() {
        val request =
            provider.buildRequest(
                apiKey = "gm-test",
                model = "gemini-2.0-flash",
                systemPrompt = "card context",
                messages =
                    listOf(
                        AiChatMessage(AiChatRole.USER, "hi"),
                        AiChatMessage(AiChatRole.ASSISTANT, "hello"),
                    ),
            )

        val json = request.bodyAsJson()
        val contents = json.getJSONArray("contents")
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertTrue(json.has("system_instruction"))
        assertEquals(
            "card context",
            json
                .getJSONObject("system_instruction")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text"),
        )
    }

    @Test
    fun `parseSseEvent extracts candidate text as a Token`() {
        val data = """{"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]}"""
        assertEquals(AiSseEvent.Token("Hi"), provider.parseSseEvent(data))
    }

    @Test
    fun `parseSseEvent ignores malformed payloads`() {
        assertEquals(AiSseEvent.Ignored, provider.parseSseEvent("not json"))
    }
}
