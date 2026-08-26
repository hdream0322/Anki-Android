// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.junit.Test

class AiProviderKindTest {
    @Test
    fun `has exactly OpenAI, Anthropic and Gemini`() {
        assertThat(
            AiProviderKind.entries.map { it.name },
            containsInAnyOrder("OPENAI", "ANTHROPIC", "GEMINI"),
        )
    }

    @Test
    fun `every entry has a distinct entryResId`() {
        val ids = AiProviderKind.entries.map { it.entryResId }
        assertThat(ids.toSet(), hasSize(ids.size))
    }
}
