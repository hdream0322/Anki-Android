// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.history

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ai.chat.AiChatBottomSheetFragment
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.ui.windows.reviewer.AiChatLaunchArgs
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiChatHistoryActivityTest : RobolectricTest() {
    @Before
    fun stubKeyStore() {
        // AndroidKeyStore, which backs the encrypted key file, is unavailable under Robolectric
        mockkObject(AiKeyStore.Companion)
        every { AiKeyStore.Companion["buildEncryptedPrefs"](any<Context>()) } returns
            targetContext.getSharedPreferences("test_ai_key_store", Context.MODE_PRIVATE)
    }

    @After
    fun unstubKeyStore() = unmockkObject(AiKeyStore.Companion)

    private fun AiChatHistoryActivity.list() = findViewById<RecyclerView>(R.id.history_list)

    private fun AiChatHistoryActivity.emptyText() = findViewById<View>(R.id.empty_text)

    private fun startHistory(): AiChatHistoryActivity = startRegularActivity<AiChatHistoryActivity>().also { advanceRobolectricLooper() }

    @Test
    fun `shows the empty state when there is no chat history`() {
        val activity = startHistory()

        assertThat(activity.emptyText().visibility, equalTo(View.VISIBLE))
        assertThat(activity.list().adapter!!.itemCount, equalTo(0))
    }

    private fun Note.chat(
        role: AiChatRole,
        content: String,
        card: Card = firstCard(),
    ) = MetaDB.storeAiChatMessage(targetContext, cid = card.id, nid = id, AiChatMessage(role, content))

    private fun AiChatHistoryActivity.rowTexts(position: Int): Pair<String, String> {
        val list = list()
        list.measure(0, 0)
        list.layout(0, 0, 1000, 2000)
        val row = list.findViewHolderForAdapterPosition(position)!!.itemView
        return row.findViewById<TextView>(R.id.title).text.toString() to row.findViewById<TextView>(R.id.preview).text.toString()
    }

    @Test
    fun `lists chatted cards newest first and hides deleted notes`() {
        val older = addBasicNote("older front", "back")
        val newer = addBasicNote("newer front", "back")
        val deleted = addBasicNote("deleted front", "back")
        older.chat(AiChatRole.USER, "q")
        newer.chat(AiChatRole.USER, "q")
        newer.chat(AiChatRole.ASSISTANT, "reply")
        deleted.chat(AiChatRole.USER, "q")
        col.removeNotes(noteIds = listOf(deleted.id))

        val activity = startHistory()

        assertThat(activity.list().adapter!!.itemCount, equalTo(2))
        assertThat(activity.emptyText().visibility, equalTo(View.GONE))
        assertThat(activity.rowTexts(0), equalTo("newer front" to "AI: reply"))
    }

    @Test
    fun `each card of a note has its own row`() {
        val note = addBasicAndReversedNote("front", "back")
        val (forward, reverse) = note.cards().sortedBy { it.ord }
        note.chat(AiChatRole.USER, "about forward", forward)
        note.chat(AiChatRole.USER, "about reverse", reverse)

        val activity = startHistory()

        assertThat(activity.list().adapter!!.itemCount, equalTo(2))
        assertThat(activity.rowTexts(0), equalTo("back" to "Me: about reverse"))
        assertThat(activity.rowTexts(1).first, equalTo("front"))
    }

    @Test
    fun `tapping a row opens that card's chat`() {
        val note = addBasicAndReversedNote("front", "back")
        val reverse = note.cards().maxBy { it.ord }
        note.chat(AiChatRole.USER, "q", reverse)
        val activity = startHistory()
        val list = activity.list()
        list.measure(0, 0)
        list.layout(0, 0, 1000, 2000)

        list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        advanceRobolectricLooper()

        val sheet = activity.supportFragmentManager.fragments.single()
        assertThat(sheet, instanceOf(AiChatBottomSheetFragment::class.java))
        val args = BundleCompat.getParcelable(sheet.requireArguments(), "launchArgs", AiChatLaunchArgs::class.java)!!
        assertThat(args.cardId, equalTo(reverse.id))
    }
}
