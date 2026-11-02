// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.MetaDB
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.Test
import org.junit.runner.RunWith

/** Test of [assignLegacyAiChats]: chats saved per note move to the note's first card */
@RunWith(AndroidJUnit4::class)
class AiChatLegacyMigrationTest : RobolectricTest() {
    @Test
    fun `note-level history moves to the note's first card only`() {
        val note = addBasicAndReversedNote("front", "back")
        val (forward, reverse) = note.cards().sortedBy { it.ord }
        createLegacyAiChats(note.id to "legacy question")

        col.assignLegacyAiChats(targetContext)

        assertThat(MetaDB.getAiChatMessages(targetContext, forward.id), contains(AiChatMessage(AiChatRole.USER, "legacy question")))
        assertThat(MetaDB.getAiChatMessages(targetContext, reverse.id), empty())
        assertThat(MetaDB.legacyAiChatNoteIds(targetContext), empty())
    }

    @Test
    fun `history of a deleted note is kept unassigned`() {
        val note = addBasicNote("front", "back")
        col.removeNotes(noteIds = listOf(note.id))
        createLegacyAiChats(note.id to "orphan")

        col.assignLegacyAiChats(targetContext)

        assertThat(MetaDB.legacyAiChatNoteIds(targetContext), contains(note.id))
    }

    /** Recreates the pre-v10 table, which had no card id, with one user message per note. */
    private fun createLegacyAiChats(vararg messages: Pair<Long, String>) {
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
            for ((nid, content) in messages) {
                db.execSQL(
                    "INSERT INTO aiChatMessages (nid, role, content, createdAt) VALUES (?, 'USER', ?, 0)",
                    arrayOf<Any>(nid, content),
                )
            }
            db.version = 9
        }
    }
}
