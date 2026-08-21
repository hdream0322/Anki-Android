// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import app.cash.turbine.test
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.AiProvider
import com.ichi2.anki.ai.AiSseEvent
import com.ichi2.anki.ai.AiStreamingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeProvider(
    private val events: List<AiSseEvent>,
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
            .url("https://example.invalid/")
            .get()
            .build()

    override fun parseSseEvent(data: String): AiSseEvent = AiSseEvent.Ignored

    fun asFlow(): Flow<AiSseEvent> = flowOf(*events.toTypedArray())
}

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `sendMessage appends the user message immediately and streams the assistant reply`() =
        runTest {
            val provider = FakeProvider(listOf(AiSseEvent.Token("Hel"), AiSseEvent.Token("lo"), AiSseEvent.Done))
            val stored = mutableListOf<AiChatMessage>()
            val viewModel =
                AiChatViewModel(
                    noteId = 1L,
                    cardContent = "Front: Q Back: A",
                    provider = provider,
                    apiKey = "key",
                    model = "fake-model",
                    streamingClient = FakeStreamingClient(provider.asFlow()),
                    storeMessage = { stored += it },
                    loadHistory = { emptyList() },
                )

            viewModel.messages.test {
                assertEquals(emptyList<AiChatMessage>(), awaitItem())

                viewModel.sendMessage("What is this?")
                assertEquals(listOf(AiChatMessage(AiChatRole.USER, "What is this?")), awaitItem())

                dispatcher.scheduler.advanceUntilIdle()

                assertEquals(
                    listOf(
                        AiChatMessage(AiChatRole.USER, "What is this?"),
                        AiChatMessage(AiChatRole.ASSISTANT, "Hel"),
                    ),
                    awaitItem(),
                )
                assertEquals(
                    listOf(
                        AiChatMessage(AiChatRole.USER, "What is this?"),
                        AiChatMessage(AiChatRole.ASSISTANT, "Hello"),
                    ),
                    awaitItem(),
                )
            }

            assertEquals(
                listOf(
                    AiChatMessage(AiChatRole.USER, "What is this?"),
                    AiChatMessage(AiChatRole.ASSISTANT, "Hello"),
                ),
                stored,
            )
        }

    @Test
    fun `sendMessage ignores a second call while a stream is already in flight`() =
        runTest {
            val provider = FakeProvider(emptyList())
            var streamInvocations = 0
            val neverCompletingFlow: Flow<AiSseEvent> =
                callbackFlow {
                    streamInvocations++
                    awaitClose {}
                }
            val stored = mutableListOf<AiChatMessage>()
            val viewModel =
                AiChatViewModel(
                    noteId = 1L,
                    cardContent = "Front: Q Back: A",
                    provider = provider,
                    apiKey = "key",
                    model = "fake-model",
                    streamingClient = FakeStreamingClient(neverCompletingFlow),
                    storeMessage = { stored += it },
                    loadHistory = { emptyList() },
                )

            viewModel.messages.test {
                assertEquals(emptyList<AiChatMessage>(), awaitItem())

                viewModel.sendMessage("First")
                assertEquals(listOf(AiChatMessage(AiChatRole.USER, "First")), awaitItem())

                assertEquals(true, viewModel.isStreaming.value)

                viewModel.sendMessage("Second")
                dispatcher.scheduler.advanceUntilIdle()

                assertEquals(listOf(AiChatMessage(AiChatRole.USER, "First")), viewModel.messages.value)
                assertEquals(1, streamInvocations)
                assertEquals(listOf(AiChatMessage(AiChatRole.USER, "First")), stored)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `buildRequestHistory leaves alternating history untouched`() {
        val history =
            listOf(
                AiChatMessage(AiChatRole.USER, "one"),
                AiChatMessage(AiChatRole.ASSISTANT, "two"),
                AiChatMessage(AiChatRole.USER, "three"),
            )

        assertEquals(history, AiChatViewModel.buildRequestHistory(history))
    }

    @Test
    fun `collapseConsecutiveSameRole merges a pair of user messages`() {
        val collapsed =
            AiChatViewModel.collapseConsecutiveSameRole(
                listOf(
                    AiChatMessage(AiChatRole.USER, "one"),
                    AiChatMessage(AiChatRole.USER, "two"),
                ),
            )

        assertEquals(listOf(AiChatMessage(AiChatRole.USER, "one\n\ntwo")), collapsed)
    }

    @Test
    fun `buildRequestHistory repairs the history a failed request leaves behind`() {
        val history =
            listOf(
                AiChatMessage(AiChatRole.USER, "one"),
                AiChatMessage(AiChatRole.ASSISTANT, "two"),
                // "three" got persisted but its reply never arrived, so "four" follows it directly
                AiChatMessage(AiChatRole.USER, "three"),
                AiChatMessage(AiChatRole.USER, "four"),
            )

        assertEquals(
            listOf(
                AiChatMessage(AiChatRole.USER, "one"),
                AiChatMessage(AiChatRole.ASSISTANT, "two"),
                AiChatMessage(AiChatRole.USER, "three\n\nfour"),
            ),
            AiChatViewModel.buildRequestHistory(history),
        )
    }

    @Test
    fun `sendMessage sends only the most recent messages once history exceeds the cap`() =
        runTest {
            val provider = FakeProvider(listOf(AiSseEvent.Token("ok"), AiSseEvent.Done))
            val history =
                (1..40).map {
                    AiChatMessage(if (it % 2 == 1) AiChatRole.USER else AiChatRole.ASSISTANT, "message $it")
                }
            val client = FakeStreamingClient(provider.asFlow())
            val viewModel =
                AiChatViewModel(
                    noteId = 1L,
                    cardContent = "Front: Q Back: A",
                    provider = provider,
                    apiKey = "key",
                    model = "fake-model",
                    streamingClient = client,
                    storeMessage = { },
                    loadHistory = { history },
                )

            viewModel.sendMessage("latest")
            dispatcher.scheduler.advanceUntilIdle()

            // the 41 messages are capped to the last 20, whose leading assistant turn is then dropped
            val sent = client.lastMessages!!
            assertEquals(19, sent.size)
            assertEquals(AiChatMessage(AiChatRole.USER, "message 23"), sent.first())
            assertEquals(AiChatMessage(AiChatRole.USER, "latest"), sent.last())
            // the full history is still displayed: 40 loaded + "latest" + the assistant reply
            assertEquals(42, viewModel.messages.value.size)
        }
}

private class FakeStreamingClient(
    private val flow: Flow<AiSseEvent>,
) : AiStreamingClient() {
    var lastMessages: List<AiChatMessage>? = null

    override fun stream(
        provider: AiProvider,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Flow<AiSseEvent> {
        lastMessages = messages
        return flow
    }
}
