// AnkiDroid/src/test/java/com/ichi2/anki/ai/AiStreamingClientTest.kt
package com.ichi2.anki.ai

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeProvider(
    private val url: String,
) : AiProvider {
    override val defaultModel = "fake-model"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request =
        Request
            .Builder()
            .url(url)
            .get()
            .build()

    override fun parseSseEvent(data: String): AiSseEvent = if (data == "[DONE]") AiSseEvent.Done else AiSseEvent.Token(data)
}

class AiStreamingClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `emits a Token event per SSE data line then Done`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: hello\n\ndata: world\n\ndata: [DONE]\n\n")
                    .build(),
            )
            val provider = FakeProvider(server.url("/").toString())
            val client = AiStreamingClient()

            client.stream(provider, apiKey = "key", model = "fake-model", systemPrompt = null, messages = emptyList()).test {
                assertEquals(AiSseEvent.Token("hello"), awaitItem())
                assertEquals(AiSseEvent.Token("world"), awaitItem())
                assertEquals(AiSseEvent.Done, awaitItem())
                awaitComplete()
            }
        }
}
