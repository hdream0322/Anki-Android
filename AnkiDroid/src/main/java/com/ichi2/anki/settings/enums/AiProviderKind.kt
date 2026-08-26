// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import com.ichi2.anki.R

/** [R.array.ai_provider_values] */
enum class AiProviderKind(
    override val entryResId: Int,
) : PrefEnum {
    OPENAI(R.string.ai_provider_openai_value),
    ANTHROPIC(R.string.ai_provider_anthropic_value),
    GEMINI(R.string.ai_provider_gemini_value),
}
