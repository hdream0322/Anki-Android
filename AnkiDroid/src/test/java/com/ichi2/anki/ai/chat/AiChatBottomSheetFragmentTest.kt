// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ui.windows.reviewer.AiChatLaunchArgs
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Test of [AiChatBottomSheetFragment] */
@RunWith(AndroidJUnit4::class)
class AiChatBottomSheetFragmentTest : RobolectricTest() {
    @Before
    fun stubKeyStore() {
        // AndroidKeyStore, which backs the encrypted key file, is unavailable under Robolectric
        mockkObject(AiKeyStore.Companion)
        every { AiKeyStore.Companion["buildEncryptedPrefs"](any<Context>()) } returns
            targetContext.getSharedPreferences("test_ai_key_store", Context.MODE_PRIVATE)
    }

    @After
    fun unstubKeyStore() = unmockkObject(AiKeyStore.Companion)

    // The message WebView has no intrinsic height: in a wrap_content sheet it collapsed to 0px,
    // hiding the whole conversation
    @Test
    fun `message list is given space in the sheet`() {
        val activity = startRegularActivity<DeckPicker>()
        val fragment = AiChatBottomSheetFragment.newInstance(AiChatLaunchArgs(noteId = 1, cardContent = "card"))
        fragment.show(activity.supportFragmentManager, null)
        shadowOf(Looper.getMainLooper()).idle()

        val messages = fragment.requireDialog().findViewById<View>(R.id.messageWebView)
        assertThat(messages.height, greaterThan(0))
    }
}
