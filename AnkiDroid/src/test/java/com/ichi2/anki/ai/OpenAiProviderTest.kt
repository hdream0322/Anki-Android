package com.ichi2.anki.ai

import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiProviderTest {
    private val provider = OpenAiProvider()

    private fun Request.bodyAsJson(): JSONObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `buildRequest targets the chat completions endpoint with bearer auth`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-test",
                model = "gpt-4o-mini",
                systemPrompt = "card context",
                messages = listOf(AiChatMessage(AiChatRole.USER, "explain this")),
            )

        assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
        assertEquals("Bearer sk-test", request.header("Authorization"))
    }

    @Test
    fun `buildRequest includes system prompt and messages in order`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-test",
                model = "gpt-4o-mini",
                systemPrompt = "card context",
                messages = listOf(AiChatMessage(AiChatRole.USER, "explain this")),
            )

        val json = request.bodyAsJson()
        assertEquals("gpt-4o-mini", json.getString("model"))
        assertTrue(json.getBoolean("stream"))
        val messages = json.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("card context", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("explain this", messages.getJSONObject(1).getString("content"))
    }

    @Test
    fun `parseSseEvent extracts delta content as a Token`() {
        val data = """{"choices":[{"delta":{"content":"Hi"}}]}"""
        assertEquals(AiSseEvent.Token("Hi"), provider.parseSseEvent(data))
    }

    @Test
    fun `parseSseEvent maps DONE sentinel to Done`() {
        assertEquals(AiSseEvent.Done, provider.parseSseEvent("[DONE]"))
    }

    @Test
    fun `parseSseEvent ignores chunks without content delta`() {
        val data = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        assertEquals(AiSseEvent.Ignored, provider.parseSseEvent(data))
    }
}
