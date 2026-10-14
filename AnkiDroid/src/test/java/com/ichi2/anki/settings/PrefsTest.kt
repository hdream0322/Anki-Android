// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import android.content.res.Resources
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.AiProviderKind
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

class PrefsTest {
    @Test
    fun `getters and setters use the same key`() {
        val sharedPrefs = SPMockBuilder().createSharedPreferences()
        val mockResources = mockk<Resources>()
        var lastKey = 0
        every { mockResources.getString(any()) } answers {
            val resId = invocation.args[0] as Int
            lastKey = resId
            resId.toString()
        }
        val prefs = PrefsRepository(sharedPrefs, mockResources)

        for (property in PrefsRepository::class.memberProperties) {
            if (property.visibility != KVisibility.PUBLIC || property !is KMutableProperty<*>) continue

            property.getter.call(prefs)
            val getterKey = lastKey

            when (property.returnType.classifier) {
                Boolean::class -> property.setter.call(prefs, false)
                String::class -> property.setter.call(prefs, "foo")
                else -> continue
            }
            val setterKey = lastKey

            assertThat("The getter and setter of '${property.name}' use the same key", getterKey, equalTo(setterKey))
        }
    }

    @Test
    fun `each AI provider keeps its own model`() {
        val prefs = mockedPrefs()
        prefs.setAiModelOverride(AiProviderKind.OPENAI, "gpt-x")
        prefs.setAiModelOverride(AiProviderKind.ANTHROPIC, "claude-x")

        prefs.aiProviderKind = AiProviderKind.ANTHROPIC
        prefs.aiProviderKind = AiProviderKind.OPENAI

        assertThat(prefs.getAiModelOverride(AiProviderKind.OPENAI), equalTo("gpt-x"))
        assertThat(prefs.getAiModelOverride(AiProviderKind.ANTHROPIC), equalTo("claude-x"))
        assertThat(prefs.getAiModelOverride(AiProviderKind.GEMINI), nullValue())
    }

    @Test
    fun `a model saved before models were per-provider belongs to the selected provider`() {
        val prefs = mockedPrefs()
        prefs.aiProviderKind = AiProviderKind.GEMINI
        prefs.sharedPrefs.edit { putString(prefs.key(R.string.pref_ai_model_key), "gemini-x") }

        assertThat(prefs.getAiModelOverride(AiProviderKind.GEMINI), equalTo("gemini-x"))
        assertThat(prefs.getAiModelOverride(AiProviderKind.OPENAI), nullValue())
        assertThat(prefs.sharedPrefs.contains(prefs.key(R.string.pref_ai_model_key)), equalTo(false))
    }

    private fun mockedPrefs(): PrefsRepository {
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { (invocation.args[0] as Int).toString() }
        return PrefsRepository(SPMockBuilder().createSharedPreferences(), mockResources)
    }
}
