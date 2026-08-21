package com.ichi2.anki.ai

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiKeyStoreTest : RobolectricTest() {
    private fun testPrefs() = targetContext.getSharedPreferences("test_ai_key_store", Context.MODE_PRIVATE)

    @Test
    fun `hasApiKey is false before any key is set`() {
        val store = AiKeyStore(targetContext, testPrefs())
        assertThat(store.hasApiKey(), `is`(false))
    }

    @Test
    fun `apiKey round-trips through storage`() {
        val prefs = testPrefs()
        AiKeyStore(targetContext, prefs).apiKey = "sk-secret"
        assertThat(AiKeyStore(targetContext, prefs).apiKey, equalTo("sk-secret"))
    }

    @Test
    fun `setting apiKey to null clears hasApiKey`() {
        val store = AiKeyStore(targetContext, testPrefs())
        store.apiKey = "sk-secret"
        store.apiKey = null
        assertThat(store.hasApiKey(), `is`(false))
    }
}
