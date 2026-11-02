// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
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
    private fun store(
        cid: Long,
        nid: Long,
        role: AiChatRole,
        content: String,
    ) = MetaDB.storeAiChatMessage(targetContext, cid = cid, nid = nid, AiChatMessage(role, content))

    @Test
    fun `getAiChatMessages is empty for a card with no history`() {
        assertThat(MetaDB.getAiChatMessages(targetContext, cid = 10L), empty())
    }

    @Test
    fun `messages are kept per card, even for cards of the same note`() {
        store(cid = 10L, nid = 1L, AiChatRole.USER, "What is this?")
        store(cid = 10L, nid = 1L, AiChatRole.ASSISTANT, "It's a flashcard.")
        store(cid = 11L, nid = 1L, AiChatRole.USER, "reverse card")

        assertThat(
            MetaDB.getAiChatMessages(targetContext, cid = 10L),
            contains(
                AiChatMessage(AiChatRole.USER, "What is this?"),
                AiChatMessage(AiChatRole.ASSISTANT, "It's a flashcard."),
            ),
        )
        assertThat(MetaDB.getAiChatMessages(targetContext, cid = 11L), contains(AiChatMessage(AiChatRole.USER, "reverse card")))
    }

    @Test
    fun `deleteAiChatMessages clears only the given card's history`() {
        store(cid = 10L, nid = 1L, AiChatRole.USER, "card 10 message")
        store(cid = 11L, nid = 1L, AiChatRole.USER, "card 11 message")

        MetaDB.deleteAiChatMessages(targetContext, cid = 10L)

        assertThat(MetaDB.getAiChatMessages(targetContext, cid = 10L), empty())
        assertThat(MetaDB.getAiChatMessages(targetContext, cid = 11L), contains(AiChatMessage(AiChatRole.USER, "card 11 message")))
    }

    @Test
    fun `hasAiChatMessages reflects whether the given card has history`() {
        store(cid = 10L, nid = 1L, AiChatRole.USER, "card 10 message")

        assertThat(MetaDB.hasAiChatMessages(targetContext, cid = 10L), equalTo(true))
        assertThat(MetaDB.hasAiChatMessages(targetContext, cid = 11L), equalTo(false))

        MetaDB.deleteAiChatMessages(targetContext, cid = 10L)

        assertThat(MetaDB.hasAiChatMessages(targetContext, cid = 10L), equalTo(false))
    }

    @Test
    fun `getAiChatSummaries is empty when there is no history`() {
        assertThat(MetaDB.getAiChatSummaries(targetContext), empty())
    }

    @Test
    fun `getAiChatSummaries returns one entry per card, most recently active first`() {
        store(cid = 10L, nid = 1L, AiChatRole.USER, "q1")
        store(cid = 10L, nid = 1L, AiChatRole.ASSISTANT, "a1")
        store(cid = 11L, nid = 1L, AiChatRole.USER, "reverse")
        store(cid = 10L, nid = 1L, AiChatRole.USER, "q2")
        store(cid = 20L, nid = 2L, AiChatRole.USER, "note 2")
        MetaDB.deleteAiChatMessages(targetContext, cid = 20L)

        val summaries = MetaDB.getAiChatSummaries(targetContext)

        assertThat(summaries.map { it.cid }, contains(10L, 11L))
        val first = summaries.first()
        assertThat(first.messageCount, equalTo(3))
        assertThat(first.lastMessage, equalTo(AiChatMessage(AiChatRole.USER, "q2")))
        assertThat(summaries[1], equalTo(AiChatSummary(11L, 1, AiChatMessage(AiChatRole.USER, "reverse"), summaries[1].lastAt)))
    }

    // Before v10 chats were stored per note, without a card id
    @Test
    fun `upgrading keeps note-level history until it is assigned to a card`() {
        createLegacyAiChatTable(nid = 1L, content = "legacy question")

        assertThat(MetaDB.legacyAiChatNoteIds(targetContext), contains(1L))
        assertThat("legacy rows belong to no card yet", MetaDB.getAiChatSummaries(targetContext), empty())

        MetaDB.assignLegacyAiChats(targetContext, nid = 1L, cid = 10L)

        assertThat(MetaDB.getAiChatMessages(targetContext, cid = 10L), contains(AiChatMessage(AiChatRole.USER, "legacy question")))
        assertThat(MetaDB.legacyAiChatNoteIds(targetContext), empty())
    }

    private fun createLegacyAiChatTable(
        nid: Long,
        content: String,
    ) {
        MetaDB.closeDB()
        targetContext.openOrCreateDatabase("ankidroid.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("DROP TABLE IF EXISTS aiChatMessages")
            db.execSQL(
                """CREATE TABLE aiChatMessages (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                nid INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL
                )""",
            )
            db.execSQL("INSERT INTO aiChatMessages (nid, role, content, createdAt) VALUES (?, 'USER', ?, 0)", arrayOf<Any>(nid, content))
            db.version = 9
        }
    }
}
