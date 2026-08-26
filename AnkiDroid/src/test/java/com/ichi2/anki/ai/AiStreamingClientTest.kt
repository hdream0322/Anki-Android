// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `emits exactly one Done from the connection-close fallback when the provider never emits its own Done`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: hello\n\ndata: world\n\n")
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

    @Test
    fun `retries a transient network failure before any token arrives, then succeeds`() =
        runTest {
            server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.ShutdownConnection).build())
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: hello\n\ndata: [DONE]\n\n")
                    .build(),
            )
            val provider = FakeProvider(server.url("/").toString())
            val client = AiStreamingClient()

            client.stream(provider, apiKey = "key", model = "fake-model", systemPrompt = null, messages = emptyList()).test {
                assertEquals(AiSseEvent.Token("hello"), awaitItem())
                assertEquals(AiSseEvent.Done, awaitItem())
                awaitComplete()
            }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `retries a 5xx response before any token arrives, then succeeds`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(503)
                    .body("server error")
                    .build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: hello\n\ndata: [DONE]\n\n")
                    .build(),
            )
            val provider = FakeProvider(server.url("/").toString())
            val client = AiStreamingClient()

            client.stream(provider, apiKey = "key", model = "fake-model", systemPrompt = null, messages = emptyList()).test {
                assertEquals(AiSseEvent.Token("hello"), awaitItem())
                assertEquals(AiSseEvent.Done, awaitItem())
                awaitComplete()
            }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `gives up and emits Http after exhausting transient retries`() =
        runTest {
            repeat(3) {
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(500)
                        .body("boom")
                        .build(),
                )
            }
            val provider = FakeProvider(server.url("/").toString())
            val client = AiStreamingClient()

            client.stream(provider, apiKey = "key", model = "fake-model", systemPrompt = null, messages = emptyList()).test {
                val error = awaitError()
                assertTrue(error is AiError.Http)
                assertEquals(500, (error as AiError.Http).code)
            }
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `retries a 429 using the Retry-After header, then succeeds`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(429)
                    .addHeader("Retry-After", "1")
                    .body("rate limited")
                    .build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: hello\n\ndata: [DONE]\n\n")
                    .build(),
            )
            val provider = FakeProvider(server.url("/").toString())
            val client = AiStreamingClient()

            client.stream(provider, apiKey = "key", model = "fake-model", systemPrompt = null, messages = emptyList()).test {
                assertEquals(AiSseEvent.Token("hello"), awaitItem())
                assertEquals(AiSseEvent.Done, awaitItem())
                awaitComplete()
            }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `gives up after a single rate-limit retry and reports the wait when the header is absent`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(429)
                    .body("rate limited")
                    .build(),
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(429)
                    .body("rate limited")
                    .build(),
            )
            val provider = FakeProvider(server.url("/").toString())
            val client = AiStreamingClient()

            client.stream(provider, apiKey = "key", model = "fake-model", systemPrompt = null, messages = emptyList()).test {
                val error = awaitError()
                assertTrue(error is AiError.RateLimited)
                assertNull((error as AiError.RateLimited).retryAfterSeconds)
            }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `does not retry once a token has already been streamed`() =
        runTest {
            // withThrottlingAndSocketEffect triggers its effect after half the body's bytes are
            // written, so pad a second (never-completed) event after the first to push that
            // halfway point past the first event's closing blank line.
            server.enqueue(
                MockResponse
                    .Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body("data: hello\n\ndata: filler-filler-filler\n")
                    .onResponseBody(SocketEffect.ShutdownConnection)
                    .build(),
            )
            val provider = FakeProvider(server.url("/").toString())
            val client = AiStreamingClient()

            client.stream(provider, apiKey = "key", model = "fake-model", systemPrompt = null, messages = emptyList()).test {
                assertEquals(AiSseEvent.Token("hello"), awaitItem())
                awaitError()
            }
            assertEquals(1, server.requestCount)
        }
}
