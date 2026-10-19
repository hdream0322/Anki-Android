// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.core.view.allViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ui.windows.reviewer.AiChatLaunchArgs
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.lessThan
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.math.roundToInt

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
    fun `message list takes the height of the conversation`() {
        val fragment = showFragment()

        fragment.onMessageListHeight(100f)
        shadowOf(Looper.getMainLooper()).idle()

        val density = fragment.resources.displayMetrics.density
        assertThat(fragment.messageList().height, equalTo((100 * density).roundToInt()))
    }

    // A short conversation must not stretch the sheet to the full screen
    @Test
    fun `a short conversation does not fill the screen`() {
        val fragment = showFragment()

        fragment.onMessageListHeight(100f)
        shadowOf(Looper.getMainLooper()).idle()

        val sheet = fragment.messageList().parent.parent as View
        val screenHeight = fragment.resources.displayMetrics.heightPixels
        assertThat(sheet.height, lessThan(screenHeight / 2))
    }

    @Test
    fun `a long conversation is limited to the screen`() {
        val fragment = showFragment()

        fragment.onMessageListHeight(100_000f)
        shadowOf(Looper.getMainLooper()).idle()

        val screenHeight = fragment.resources.displayMetrics.heightPixels
        assertThat(fragment.messageList().height, allOf(greaterThan(0), lessThan(screenHeight)))
    }

    // A snackbar shown on the activity is hidden behind the sheet
    @Test
    fun `missing API key snackbar is shown inside the sheet`() {
        val fragment = showFragment()
        val sheet = fragment.requireDialog()

        sheet.findViewById<EditText>(R.id.messageInput).setText("question")
        sheet.findViewById<View>(R.id.sendButton).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val snackbars =
            sheet.window!!
                .decorView.allViews
                .filterIsInstance<Snackbar.SnackbarLayout>()
        assertThat(snackbars.count(), equalTo(1))
    }

    private fun showFragment(): AiChatBottomSheetFragment {
        val activity = startRegularActivity<DeckPicker>()
        val fragment = AiChatBottomSheetFragment.newInstance(AiChatLaunchArgs(noteId = 1, cardContent = "card"))
        fragment.show(activity.supportFragmentManager, null)
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    private fun AiChatBottomSheetFragment.messageList() = requireDialog().findViewById<View>(R.id.messageWebView)
}
