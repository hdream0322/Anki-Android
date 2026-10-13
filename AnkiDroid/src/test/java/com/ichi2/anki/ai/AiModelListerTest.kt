// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

/** Pages through `{"ids":[...],"next":...}` bodies, sending the token as `?page=`. */
private class PagingFakeProvider(
    private val baseUrl: String,
) : AiProvider {
    override val defaultModel = "fake-model"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request = throw UnsupportedOperationException()

    override fun parseSseEvent(data: String): AiSseEvent = AiSseEvent.Ignored

    override fun buildListModelsRequest(
        apiKey: String,
        pageToken: String?,
    ): Request =
        Request
            .Builder()
            .url(if (pageToken == null) baseUrl else "$baseUrl?page=$pageToken")
            .header("x-key", apiKey)
            .get()
            .build()

    override fun parseModelsPage(body: String): AiModelsPage {
        val json = JSONObject(body)
        val ids = json.getJSONArray("ids")
        return AiModelsPage(
            modelIds = (0 until ids.length()).map { ids.getString(it) },
            nextPageToken = json.optString("next").ifEmpty { null },
        )
    }
}

class AiModelListerTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: PagingFakeProvider
    private val lister = AiModelLister()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = PagingFakeProvider(server.url("/models").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    @Test
    fun `follows page tokens until the last page and removes duplicates`() =
        runTest {
            enqueue("""{"ids":["a","b"],"next":"p2"}""")
            enqueue("""{"ids":["b","c"]}""")

            val models = lister.listModels(provider, apiKey = "key")

            assertEquals(listOf("a", "b", "c"), models)
            assertEquals("key", server.takeRequest().headers["x-key"])
            assertEquals("p2", server.takeRequest().url.queryParameter("page"))
        }

    @Test
    fun `blank api key fails without a network call`() =
        runTest {
            assertFailsWith<AiError.MissingApiKey> { lister.listModels(provider, apiKey = " ") }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `a key OkHttp rejects as a header value fails without exposing the key`() =
        runTest {
            val error = assertFailsWith<AiError.MissingApiKey> { lister.listModels(provider, apiKey = "secret\u200Bkey") }

            assertFalse(error.toString().contains("secret"))
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `http error is surfaced as AiError Http`() =
        runTest {
            enqueue("""{"error":"bad key"}""", code = 401)

            val error = assertFailsWith<AiError.Http> { lister.listModels(provider, apiKey = "key") }
            assertEquals(401, error.code)
        }

    @Test
    fun `429 is surfaced as AiError RateLimited`() =
        runTest {
            enqueue("{}", code = 429)

            assertFailsWith<AiError.RateLimited> { lister.listModels(provider, apiKey = "key") }
        }

    @Test
    fun `malformed body is surfaced as AiError Http`() =
        runTest {
            enqueue("not json")

            val error = assertFailsWith<AiError.Http> { lister.listModels(provider, apiKey = "key") }
            assertTrue(error.message!!.startsWith("HTTP 200"))
        }

    @Test
    fun `connection failure is surfaced as AiError Network`() =
        runTest {
            server.close()

            assertFailsWith<AiError.Network> { lister.listModels(provider, apiKey = "key") }
        }
}
