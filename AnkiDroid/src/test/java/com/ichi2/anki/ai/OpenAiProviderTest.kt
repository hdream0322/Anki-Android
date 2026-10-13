// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNull

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

    @Test
    fun `buildListModelsRequest targets the models endpoint with bearer auth`() {
        val request = provider.buildListModelsRequest(apiKey = "sk-test", pageToken = null)

        assertEquals("GET", request.method)
        assertEquals("https://api.openai.com/v1/models", request.url.toString())
        assertEquals("Bearer sk-test", request.header("Authorization"))
    }

    @Test
    fun `parseModelsPage keeps only chat models, with no next page`() {
        val body =
            """
            {"object":"list","data":[
              {"id":"gpt-5.6-luna","object":"model","created":400,"owned_by":"openai"},
              {"id":"text-embedding-3-small","object":"model","created":300,"owned_by":"openai"},
              {"id":"gpt-4o-mini-tts","object":"model","created":500,"owned_by":"openai"},
              {"id":"gpt-4o-realtime-preview","object":"model","created":500,"owned_by":"openai"},
              {"id":"gpt-image-1","object":"model","created":500,"owned_by":"openai"},
              {"id":"whisper-1","object":"model","created":500,"owned_by":"openai"},
              {"id":"omni-moderation-latest","object":"model","created":500,"owned_by":"openai"},
              {"id":"gpt-3.5-turbo-instruct","object":"model","created":500,"owned_by":"openai"}
            ]}
            """.trimIndent()

        val page = provider.parseModelsPage(body)

        assertEquals(listOf("gpt-5.6-luna"), page.models.map { it.id })
        assertNull(page.nextPageToken)
    }

    @Test
    fun `parseModelsPage tiers models by their lineup codename`() {
        val body =
            """
            {"object":"list","data":[
              {"id":"gpt-6-astra","created":1},
              {"id":"gpt-5.5-pro","created":1},
              {"id":"o3-pro","created":1},
              {"id":"gpt-6.1-sol","created":1},
              {"id":"gpt-5.6-terra","created":1},
              {"id":"gpt-5.6-luna","created":1},
              {"id":"gpt-5.4-nano","created":1},
              {"id":"gpt-5.4-mini","created":1},
              {"id":"o4-mini","created":1}
            ]}
            """.trimIndent()

        val tiers = provider.parseModelsPage(body).models.associate { it.id to it.tier }

        assertEquals(
            mapOf(
                "gpt-6-astra" to AiModelTier.POWERFUL,
                "gpt-5.5-pro" to AiModelTier.POWERFUL,
                "o3-pro" to AiModelTier.POWERFUL,
                "gpt-6.1-sol" to AiModelTier.BALANCED,
                "gpt-5.6-terra" to AiModelTier.BALANCED,
                "gpt-5.6-luna" to AiModelTier.FAST,
                "gpt-5.4-nano" to AiModelTier.FAST,
                "gpt-5.4-mini" to AiModelTier.FAST,
                "o4-mini" to AiModelTier.FAST,
            ),
            tiers,
        )
    }

    @Test
    fun `parseModelsPage leaves models without a lineup codename untiered`() {
        val body =
            """
            {"object":"list","data":[
              {"id":"gpt-3.5-turbo","created":1},
              {"id":"gpt-4o","created":1},
              {"id":"gpt-5","created":1},
              {"id":"o3","created":1},
              {"id":"chatgpt-4o-latest","created":1}
            ]}
            """.trimIndent()

        assertEquals(listOf<AiModelTier?>(null, null, null, null, null), provider.parseModelsPage(body).models.map { it.tier })
    }

    @Test
    fun `parseModelsPage lists tiered models newest first, then untiered ones newest first`() {
        val body =
            """
            {"object":"list","data":[
              {"id":"gpt-4o","created":300},
              {"id":"gpt-5.6-luna","created":100},
              {"id":"gpt-3.5-turbo","created":50},
              {"id":"gpt-6-astra","created":200}
            ]}
            """.trimIndent()

        assertEquals(
            listOf(
                AiModelInfo("gpt-6-astra", tier = AiModelTier.POWERFUL, createdEpochSeconds = 200),
                AiModelInfo("gpt-5.6-luna", tier = AiModelTier.FAST, createdEpochSeconds = 100),
                AiModelInfo("gpt-4o", createdEpochSeconds = 300),
                AiModelInfo("gpt-3.5-turbo", createdEpochSeconds = 50),
            ),
            provider.parseModelsPage(body).models,
        )
    }
}
