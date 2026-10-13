// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.test.assertNull

class AnthropicProviderTest {
    private val provider = AnthropicProvider()

    private fun Request.bodyAsJson(): JSONObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `buildRequest targets the messages endpoint with x-api-key auth`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-ant-test",
                model = "claude-3-5-haiku-latest",
                systemPrompt = "card context",
                messages = listOf(AiChatMessage(AiChatRole.USER, "explain this")),
            )

        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("sk-ant-test", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
    }

    @Test
    fun `buildRequest puts system prompt in top-level field, not in messages`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-ant-test",
                model = "claude-3-5-haiku-latest",
                systemPrompt = "card context",
                messages = listOf(AiChatMessage(AiChatRole.USER, "explain this")),
            )

        val json = request.bodyAsJson()
        assertEquals("card context", json.getString("system"))
        assertEquals(1, json.getJSONArray("messages").length())
        assertEquals("user", json.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test
    fun `buildRequest omits system field when systemPrompt is null`() {
        val request =
            provider.buildRequest(
                apiKey = "sk-ant-test",
                model = "claude-3-5-haiku-latest",
                systemPrompt = null,
                messages = listOf(AiChatMessage(AiChatRole.USER, "hi")),
            )

        assertFalse(request.bodyAsJson().has("system"))
    }

    @Test
    fun `parseSseEvent extracts text_delta as a Token`() {
        val data = """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hi"}}"""
        assertEquals(AiSseEvent.Token("Hi"), provider.parseSseEvent(data))
    }

    @Test
    fun `parseSseEvent maps message_stop to Done`() {
        val data = """{"type":"message_stop"}"""
        assertEquals(AiSseEvent.Done, provider.parseSseEvent(data))
    }

    @Test
    fun `parseSseEvent ignores unrelated event types`() {
        val data = """{"type":"message_start"}"""
        assertEquals(AiSseEvent.Ignored, provider.parseSseEvent(data))
    }

    @Test
    fun `buildListModelsRequest targets the models endpoint with max page size`() {
        val request = provider.buildListModelsRequest(apiKey = "sk-ant-test", pageToken = null)

        assertEquals("GET", request.method)
        assertEquals("https://api.anthropic.com/v1/models?limit=1000", request.url.toString())
        assertEquals("sk-ant-test", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
    }

    @Test
    fun `buildListModelsRequest passes the page token as after_id`() {
        val request = provider.buildListModelsRequest(apiKey = "sk-ant-test", pageToken = "claude-x")

        assertEquals("claude-x", request.url.queryParameter("after_id"))
    }

    @Test
    fun `parseModelsPage keeps API order and returns last_id while has_more`() {
        val body =
            """
            {"data":[
              {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5"},
              {"type":"model","id":"claude-haiku-5-5","display_name":"Claude Haiku 5.5"}
            ],"first_id":"claude-opus-5-5","has_more":true,"last_id":"claude-haiku-5-5"}
            """.trimIndent()

        val page = provider.parseModelsPage(body)

        assertEquals(listOf("claude-opus-5-5", "claude-haiku-5-5"), page.modelIds)
        assertEquals("claude-haiku-5-5", page.nextPageToken)
    }

    @Test
    fun `parseModelsPage returns no next page when has_more is false`() {
        val body = """{"data":[{"type":"model","id":"claude-haiku-5-5"}],"has_more":false,"last_id":"claude-haiku-5-5"}"""

        assertNull(provider.parseModelsPage(body).nextPageToken)
    }
}
