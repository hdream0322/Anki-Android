/*
 *  Copyright (c) 2026 Deurim Fork
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ai.AnthropicProvider
import com.ichi2.anki.ai.GeminiProvider
import com.ichi2.anki.ai.OpenAiProvider
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AiProviderKind
import com.ichi2.anki.ui.windows.reviewer.whiteboard.showColorPickerDialog
import com.ichi2.anki.update.UpdateManager

/** Aggregates every fork-only preference (updater, whiteboard, SFX) in one screen. */
class DeurimSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_deurim
    override val analyticsScreenNameConstant: String
        get() = "prefs.deurim"

    override fun initSubscreen() {
        requirePreference<Preference>(R.string.pref_check_update_now_key).setOnPreferenceClickListener {
            UpdateManager.checkNow(requireActivity())
            true
        }
        initReviewProgressBarColorPref()
        initAiApiKeyPref()
        initAiModelPref()
    }

    private fun initReviewProgressBarColorPref() {
        requirePreference<Preference>(R.string.pref_review_progress_bar_color_key).apply {
            setOnPreferenceClickListener {
                requireContext().showColorPickerDialog(Prefs.reviewProgressBarColor) { color ->
                    Prefs.reviewProgressBarColor = color
                }
                true
            }
        }
    }

    private fun initAiApiKeyPref() {
        // AndroidKeyStore (which EncryptedSharedPreferences/AiKeyStore depends on) is unavailable
        // under Robolectric, so constructing it here would crash any unit test that creates this
        // fragment. On a real device AndroidKeyStore is always present.
        if (isRunningAsUnitTest) return
        val keyStore = AiKeyStore(requireContext())
        requirePreference<EditTextPreference>(R.string.pref_ai_api_key_key).apply {
            isPersistent = false
            // Write-only: the field always opens blank, even once a key is set — there is no way
            // to view or edit the stored key, only to replace it with a brand-new one.
            text = null
            summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    if (keyStore.hasApiKey()) {
                        getString(R.string.pref_ai_api_key_summary_set)
                    } else {
                        getString(R.string.pref_ai_api_key_summary_not_set)
                    }
                }
            setOnPreferenceChangeListener { preference, newValue ->
                val trimmed = (newValue as String).trim()
                // An empty submission must not clear an existing key — only a non-blank entry
                // replaces it.
                if (trimmed.isNotEmpty()) {
                    keyStore.apiKey = trimmed
                }
                (preference as EditTextPreference).text = null
                true
            }
        }
    }

    private fun initAiModelPref() {
        val modelPref = requirePreference<EditTextPreference>(R.string.pref_ai_model_key)
        val modelSummaryProvider =
            Preference.SummaryProvider<EditTextPreference> {
                val override = Prefs.aiModelOverride
                if (override.isNullOrBlank()) {
                    getString(R.string.pref_ai_model_summary_not_set, defaultModelFor(Prefs.aiProviderKind))
                } else {
                    getString(R.string.pref_ai_model_summary_set, override)
                }
            }
        modelPref.apply {
            text = Prefs.aiModelOverride
            summaryProvider = modelSummaryProvider
            setOnPreferenceChangeListener { _, newValue ->
                Prefs.aiModelOverride = (newValue as String).trim().ifBlank { null }
                true
            }
        }

        // The "not set" summary shows the selected provider's default model, so it must refresh
        // whenever the provider changes. Re-setting the same SummaryProvider is the public-API
        // way to force Preference to re-evaluate it (notifyChanged() itself is protected).
        requirePreference<ListPreference>(R.string.pref_ai_provider_key).setOnPreferenceChangeListener { _, _ ->
            modelPref.summaryProvider = modelSummaryProvider
            true
        }
    }

    private fun defaultModelFor(providerKind: AiProviderKind): String =
        when (providerKind) {
            AiProviderKind.OPENAI -> OpenAiProvider().defaultModel
            AiProviderKind.ANTHROPIC -> AnthropicProvider().defaultModel
            AiProviderKind.GEMINI -> GeminiProvider().defaultModel
        }
}
