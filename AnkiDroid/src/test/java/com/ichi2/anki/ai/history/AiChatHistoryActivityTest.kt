// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.history

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ai.chat.AiChatBottomSheetFragment
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

    @Test
    fun `lists chatted notes newest first and hides deleted notes`() {
        val older = addBasicNote("older front", "back")
        val newer = addBasicNote("newer front", "back")
        val deleted = addBasicNote("deleted front", "back")
        MetaDB.storeAiChatMessage(targetContext, older.id, AiChatMessage(AiChatRole.USER, "q"))
        MetaDB.storeAiChatMessage(targetContext, newer.id, AiChatMessage(AiChatRole.USER, "q"))
        MetaDB.storeAiChatMessage(targetContext, newer.id, AiChatMessage(AiChatRole.ASSISTANT, "reply"))
        MetaDB.storeAiChatMessage(targetContext, deleted.id, AiChatMessage(AiChatRole.USER, "q"))
        col.removeNotes(noteIds = listOf(deleted.id))

        val activity = startHistory()
        val list = activity.list()
        list.measure(0, 0)
        list.layout(0, 0, 1000, 2000)

        assertThat(list.adapter!!.itemCount, equalTo(2))
        assertThat(activity.emptyText().visibility, equalTo(View.GONE))
        val firstRow = list.findViewHolderForAdapterPosition(0)!!.itemView
        assertThat(firstRow.findViewById<TextView>(R.id.title).text.toString(), equalTo("newer front"))
        assertThat(firstRow.findViewById<TextView>(R.id.preview).text.toString(), equalTo("AI: reply"))
    }

    @Test
    fun `tapping a row opens that note's chat`() {
        val note = addBasicNote("front", "back")
        MetaDB.storeAiChatMessage(targetContext, note.id, AiChatMessage(AiChatRole.USER, "q"))
        val activity = startHistory()
        val list = activity.list()
        list.measure(0, 0)
        list.layout(0, 0, 1000, 2000)

        list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        advanceRobolectricLooper()

        val sheet = activity.supportFragmentManager.fragments.single()
        assertThat(sheet, instanceOf(AiChatBottomSheetFragment::class.java))
    }
}
