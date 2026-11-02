// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.history

import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.AiChatSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatHistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun summary(
        nid: Long,
        count: Int,
        last: String,
    ) = AiChatSummary(nid, count, AiChatMessage(AiChatRole.USER, last), lastAt = nid * 1000)

    @Test
    fun `items keep the summary order and use the resolved titles`() =
        runTest(dispatcher) {
            val viewModel =
                AiChatHistoryViewModel(
                    loadSummaries = { listOf(summary(2, 1, "b"), summary(1, 3, "a")) },
                    resolveTitles = { nids -> nids.associateWith { "title $it" } },
                )
            advanceUntilIdle()

            val items = viewModel.items.value!!
            assertEquals(listOf(2L, 1L), items.map { it.nid })
            assertEquals("title 1", items[1].title)
            assertEquals(3, items[1].messageCount)
            assertEquals(AiChatMessage(AiChatRole.USER, "a"), items[1].lastMessage)
        }

    @Test
    fun `notes missing from the collection are left out`() =
        runTest(dispatcher) {
            val viewModel =
                AiChatHistoryViewModel(
                    loadSummaries = { listOf(summary(2, 1, "b"), summary(1, 3, "a")) },
                    resolveTitles = { mapOf(1L to "kept") },
                )
            advanceUntilIdle()

            assertEquals(listOf(1L), viewModel.items.value!!.map { it.nid })
        }

    @Test
    fun `refresh reloads the summaries`() =
        runTest(dispatcher) {
            var summaries = listOf(summary(1, 1, "a"))
            val viewModel =
                AiChatHistoryViewModel(
                    loadSummaries = { summaries },
                    resolveTitles = { nids -> nids.associateWith { "t" } },
                )
            advanceUntilIdle()
            summaries = emptyList()

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(emptyList<AiChatHistoryItem>(), viewModel.items.value)
        }

    @Test
    fun `a failed refresh keeps the previous items`() =
        runTest(dispatcher) {
            var fail = false
            val viewModel =
                AiChatHistoryViewModel(
                    loadSummaries = { if (fail) error("collection closed") else listOf(summary(1, 1, "a")) },
                    resolveTitles = { nids -> nids.associateWith { "t" } },
                )
            advanceUntilIdle()
            fail = true

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(listOf(1L), viewModel.items.value!!.map { it.nid })
        }
}
