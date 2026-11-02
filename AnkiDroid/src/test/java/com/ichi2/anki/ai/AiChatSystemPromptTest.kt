// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiChatSystemPromptTest : RobolectricTest() {
    @Test
    fun `omits study stats by default`() {
        val card = addBasicNote("Q", "A").firstCard()

        assertEquals(false, col.aiChatSystemPrompt(card).contains("<study_stats>\ncard_type: "))
    }

    @Test
    fun `includes study stats from the collection when enabled`() {
        targetContext.sharedPrefs().edit { putBoolean(targetContext.getString(R.string.pref_ai_include_study_stats_key), true) }
        val card = addBasicNote("Q", "A").firstCard()

        val prompt = col.aiChatSystemPrompt(card)

        assertEquals(true, prompt.contains("<study_stats>\ncard_type: "))
        assertEquals(true, prompt.contains("\nreviews: 0\nlapses: 0\n"))
    }
}
