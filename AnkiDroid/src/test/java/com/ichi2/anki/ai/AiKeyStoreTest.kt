// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.enums.AiProviderKind
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiKeyStoreTest : RobolectricTest() {
    private fun testPrefs() = targetContext.getSharedPreferences("test_ai_key_store", Context.MODE_PRIVATE)

    private fun store(
        prefs: android.content.SharedPreferences = testPrefs(),
        legacyKeyOwner: AiProviderKind = AiProviderKind.OPENAI,
    ) = AiKeyStore(targetContext, prefs) { legacyKeyOwner }

    @Test
    fun `hasApiKey is false before any key is set`() {
        assertThat(store().hasApiKey(AiProviderKind.OPENAI), `is`(false))
    }

    @Test
    fun `apiKey round-trips through storage`() {
        val prefs = testPrefs()
        store(prefs).setApiKey(AiProviderKind.OPENAI, "sk-secret")
        assertThat(store(prefs).getApiKey(AiProviderKind.OPENAI), equalTo("sk-secret"))
    }

    @Test
    fun `setting apiKey to null clears hasApiKey`() {
        val store = store()
        store.setApiKey(AiProviderKind.OPENAI, "sk-secret")
        store.setApiKey(AiProviderKind.OPENAI, null)
        assertThat(store.hasApiKey(AiProviderKind.OPENAI), `is`(false))
    }

    @Test
    fun `each provider keeps its own key`() {
        val store = store()
        store.setApiKey(AiProviderKind.OPENAI, "sk-openai")
        store.setApiKey(AiProviderKind.ANTHROPIC, "sk-ant")

        assertThat(store.getApiKey(AiProviderKind.OPENAI), equalTo("sk-openai"))
        assertThat(store.getApiKey(AiProviderKind.ANTHROPIC), equalTo("sk-ant"))
        assertThat(store.getApiKey(AiProviderKind.GEMINI), nullValue())
    }

    @Test
    fun `a key saved before per-provider keys moves to the provider selected at the time`() {
        val prefs = testPrefs()
        prefs.edit { putString("api_key", "sk-ant-legacy") }

        val store = store(prefs, legacyKeyOwner = AiProviderKind.ANTHROPIC)

        assertThat(store.getApiKey(AiProviderKind.ANTHROPIC), equalTo("sk-ant-legacy"))
        assertThat(store.getApiKey(AiProviderKind.OPENAI), nullValue())
        assertThat(prefs.contains("api_key"), `is`(false))
    }
}
