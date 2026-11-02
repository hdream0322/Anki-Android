// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.AiChatSummary
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetaDBAiChatTest : RobolectricTest() {
    @Test
    fun `getAiChatMessages is empty for a note with no history`() {
        assertThat(MetaDB.getAiChatMessages(targetContext, nid = 1L), empty())
    }

    @Test
    fun `stored messages are returned in insertion order for the given note`() {
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.USER, "What is this?"))
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.ASSISTANT, "It's a flashcard."))
        MetaDB.storeAiChatMessage(targetContext, nid = 2L, AiChatMessage(AiChatRole.USER, "unrelated note"))

        assertThat(
            MetaDB.getAiChatMessages(targetContext, nid = 1L),
            contains(
                AiChatMessage(AiChatRole.USER, "What is this?"),
                AiChatMessage(AiChatRole.ASSISTANT, "It's a flashcard."),
            ),
        )
    }

    @Test
    fun `deleteAiChatMessages clears only the given note's history`() {
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.USER, "note 1 message"))
        MetaDB.storeAiChatMessage(targetContext, nid = 2L, AiChatMessage(AiChatRole.USER, "note 2 message"))

        MetaDB.deleteAiChatMessages(targetContext, nid = 1L)

        assertThat(MetaDB.getAiChatMessages(targetContext, nid = 1L), empty())
        assertThat(
            MetaDB.getAiChatMessages(targetContext, nid = 2L),
            contains(AiChatMessage(AiChatRole.USER, "note 2 message")),
        )
    }

    @Test
    fun `hasAiChatMessages reflects whether the given note has history`() {
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.USER, "note 1 message"))

        assertThat(MetaDB.hasAiChatMessages(targetContext, nid = 1L), equalTo(true))
        assertThat(MetaDB.hasAiChatMessages(targetContext, nid = 2L), equalTo(false))

        MetaDB.deleteAiChatMessages(targetContext, nid = 1L)

        assertThat(MetaDB.hasAiChatMessages(targetContext, nid = 1L), equalTo(false))
    }

    @Test
    fun `getAiChatSummaries is empty when there is no history`() {
        assertThat(MetaDB.getAiChatSummaries(targetContext), empty())
    }

    @Test
    fun `getAiChatSummaries returns one entry per note, most recently active first`() {
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.USER, "q1"))
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.ASSISTANT, "a1"))
        MetaDB.storeAiChatMessage(targetContext, nid = 2L, AiChatMessage(AiChatRole.USER, "note 2"))
        MetaDB.storeAiChatMessage(targetContext, nid = 1L, AiChatMessage(AiChatRole.USER, "q2"))
        MetaDB.storeAiChatMessage(targetContext, nid = 3L, AiChatMessage(AiChatRole.USER, "note 3"))
        MetaDB.deleteAiChatMessages(targetContext, nid = 3L)

        val summaries = MetaDB.getAiChatSummaries(targetContext)

        assertThat(summaries.map { it.nid }, contains(1L, 2L))
        val first = summaries.first()
        assertThat(first.messageCount, equalTo(3))
        assertThat(first.lastMessage, equalTo(AiChatMessage(AiChatRole.USER, "q2")))
        assertThat(summaries[1], equalTo(AiChatSummary(2L, 1, AiChatMessage(AiChatRole.USER, "note 2"), summaries[1].lastAt)))
    }
}
