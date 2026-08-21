// AnkiDroid/src/test/java/com/ichi2/anki/ai/chat/AiChatViewModelTest.kt
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
}

private class FakeStreamingClient(
    private val flow: Flow<AiSseEvent>,
) : AiStreamingClient() {
    override fun stream(
        provider: AiProvider,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Flow<AiSseEvent> = flow
}
