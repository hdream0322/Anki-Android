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
import androidx.preference.Preference
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import com.ichi2.anki.settings.Prefs
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
            text = keyStore.apiKey
            summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    if (keyStore.hasApiKey()) {
                        getString(R.string.pref_ai_api_key_summary_set)
                    } else {
                        getString(R.string.pref_ai_api_key_summary_not_set)
                    }
                }
            setOnPreferenceChangeListener { preference, newValue ->
                keyStore.apiKey = (newValue as String).trim().ifBlank { null }
                (preference as EditTextPreference).text = keyStore.apiKey
                true
            }
        }
    }
}
