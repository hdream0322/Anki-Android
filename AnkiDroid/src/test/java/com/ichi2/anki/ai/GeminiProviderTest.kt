// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNull

class GeminiProviderTest {
    private val provider = GeminiProvider()

    private fun Request.bodyAsJson(): JSONObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `buildRequest targets streamGenerateContent with key in a header`() {
        val request =
            provider.buildRequest(
                apiKey = "gm-test",
                model = "gemini-2.0-flash",
                systemPrompt = null,
                messages = listOf(AiChatMessage(AiChatRole.USER, "hi")),
            )

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse",
            request.url.toString(),
        )
        assertEquals("gm-test", request.header("x-goog-api-key"))
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

    @Test
    fun `buildListModelsRequest targets the models endpoint with the key in a header`() {
        val request = provider.buildListModelsRequest(apiKey = "g-test", pageToken = null)

        assertEquals("GET", request.method)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", request.url.toString())
        assertEquals("g-test", request.header("x-goog-api-key"))
    }

    @Test
    fun `buildListModelsRequest passes the page token`() {
        val request = provider.buildListModelsRequest(apiKey = "g-test", pageToken = "abc")

        assertEquals("abc", request.url.queryParameter("pageToken"))
    }

    @Test
    fun `parseModelsPage keeps generateContent models and strips the models prefix`() {
        val body =
            """
            {"models":[
              {"name":"models/gemini-3.8-flash","displayName":"Gemini 3.8 Flash","description":"Fast model","inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent","countTokens"]},
              {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]},
              {"name":"models/gemini-3.8-pro","supportedGenerationMethods":["generateContent"]}
            ],"nextPageToken":"next"}
            """.trimIndent()

        val page = provider.parseModelsPage(body)

        assertEquals(
            listOf(
                AiModelInfo("gemini-3.8-flash", displayName = "Gemini 3.8 Flash", description = "Fast model", inputTokenLimit = 1_048_576),
                AiModelInfo("gemini-3.8-pro"),
            ),
            page.models,
        )
        assertEquals("next", page.nextPageToken)
    }

    @Test
    fun `parseModelsPage returns no next page when the token is absent`() {
        val body = """{"models":[{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]}]}"""

        assertNull(provider.parseModelsPage(body).nextPageToken)
    }
}
