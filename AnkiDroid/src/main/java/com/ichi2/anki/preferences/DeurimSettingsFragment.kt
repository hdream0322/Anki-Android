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

import android.graphics.Color
import android.os.Build
import android.text.InputType
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiError
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ai.AiModelInfo
import com.ichi2.anki.ai.AiModelLister
import com.ichi2.anki.ai.AiProvider
import com.ichi2.anki.ai.AnthropicProvider
import com.ichi2.anki.ai.GeminiProvider
import com.ichi2.anki.ai.OpenAiProvider
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AiProviderKind
import com.ichi2.anki.ui.windows.reviewer.whiteboard.showColorPickerDialog
import com.ichi2.anki.update.UpdateManager
import com.ichi2.anki.withProgress
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date
import kotlin.math.roundToLong

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
            setOnBindEditTextListener { editText ->
                // Mask input like a password field so the key isn't shown on screen while typing,
                // and opt out of the autofill framework so a password manager doesn't also end up
                // holding a copy of it outside AiKeyStore's encrypted storage.
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    editText.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                }
            }
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
        val modelPref = requirePreference<Preference>(R.string.pref_ai_model_key)
        val modelSummaryProvider =
            Preference.SummaryProvider<Preference> {
                val override = Prefs.aiModelOverride
                if (override.isNullOrBlank()) {
                    getString(R.string.pref_ai_model_summary_not_set, providerFor(Prefs.aiProviderKind).defaultModel)
                } else {
                    getString(R.string.pref_ai_model_summary_set, override)
                }
            }
        // Re-setting the same SummaryProvider is the public-API way to force Preference to
        // re-evaluate it (notifyChanged() itself is protected).
        val setModel = { model: String? ->
            Prefs.aiModelOverride = model
            modelPref.summaryProvider = modelSummaryProvider
        }
        modelPref.apply {
            summaryProvider = modelSummaryProvider
            setOnPreferenceClickListener {
                launchCatchingTask { showModelPicker(setModel) }
                true
            }
        }

        // A model id only exists at the provider it came from, so switching provider drops the
        // override instead of sending e.g. a `claude-*` id to OpenAI. The summary also shows the
        // selected provider's default model, so it must refresh either way.
        requirePreference<ListPreference>(R.string.pref_ai_provider_key).setOnPreferenceChangeListener { _, newValue ->
            if (newValue != getString(Prefs.aiProviderKind.entryResId)) {
                setModel(null)
            } else {
                modelPref.summaryProvider = modelSummaryProvider
            }
            true
        }
    }

    /**
     * Offers the models the user's key can access, fetched live from the provider's official
     * model-list API. Without a key, or if the fetch fails, only "default" and "custom" remain
     * so the setting is never blocked by connectivity.
     */
    private suspend fun showModelPicker(setModel: (String?) -> Unit) {
        val provider = providerFor(Prefs.aiProviderKind)
        val context = requireContext()
        // Opening AiKeyStore touches AndroidKeyStore and disk, so keep it off the main thread.
        val apiKey = if (isRunningAsUnitTest) null else withContext(Dispatchers.IO) { AiKeyStore(context).apiKey }
        val fetchedModels =
            if (apiKey.isNullOrBlank()) {
                showThemedToast(requireContext(), R.string.ai_model_list_no_key, false)
                emptyList()
            } else {
                try {
                    withProgress(R.string.ai_model_list_loading) { AiModelLister().listModels(provider, apiKey) }
                } catch (e: AiError) {
                    Timber.w(e, "Could not fetch the AI model list")
                    showThemedToast(requireContext(), R.string.ai_model_list_failed, false)
                    emptyList()
                }
            }

        val current = Prefs.aiModelOverride
        val fetchedIds = fetchedModels.map { it.id }
        val labels =
            listOf<CharSequence>(
                getString(R.string.ai_model_item_default, provider.defaultModel),
                getString(R.string.ai_model_item_custom),
            ) + fetchedModels.map { modelLabel(it) }
        val checkedIndex =
            when {
                current.isNullOrBlank() -> 0
                current in fetchedIds -> FIXED_MODEL_ITEMS + fetchedIds.indexOf(current)
                else -> 1
            }
        AlertDialog.Builder(requireContext()).show {
            title(R.string.pref_ai_model_title)
            setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> setModel(null)
                    1 -> showCustomModelDialog(current, setModel)
                    else -> setModel(fetchedIds[which - FIXED_MODEL_ITEMS])
                }
            }
            negativeButton(CommonString.dialog_cancel)
        }
    }

    /** The model id, followed by a smaller, dimmed line with whatever details the vendor's list gave. */
    private fun modelLabel(model: AiModelInfo): CharSequence {
        val details =
            listOfNotNull(
                model.displayName?.takeIf { it != model.id },
                model.description,
                model.inputTokenLimit?.let { getString(R.string.ai_model_detail_context, formatTokenCount(it)) },
                model.createdEpochSeconds?.let {
                    getString(R.string.ai_model_detail_released, DateFormat.getMediumDateFormat(requireContext()).format(Date(it * 1000)))
                },
                getString(R.string.ai_model_detail_deprecated).takeIf { model.isDeprecated },
            ).joinToString(" · ")
        if (details.isEmpty()) return model.id
        val detailColor = MaterialColors.getColor(requireContext(), android.R.attr.textColorSecondary, Color.GRAY)
        return buildSpannedString {
            append(model.id)
            append('\n')
            inSpans(RelativeSizeSpan(DETAIL_TEXT_SCALE), ForegroundColorSpan(detailColor)) { append(details) }
        }
    }

    /** e.g. 1048576 -> "1M", 200000 -> "200K". */
    private fun formatTokenCount(tokens: Long): String =
        when {
            tokens >= 1_000_000 -> "${(tokens / 1_000_000.0).roundToLong()}M"
            tokens >= 1_000 -> "${(tokens / 1_000.0).roundToLong()}K"
            else -> tokens.toString()
        }

    private fun showCustomModelDialog(
        current: String?,
        setModel: (String?) -> Unit,
    ) {
        AlertDialog
            .Builder(requireContext())
            .show {
                title(R.string.ai_model_item_custom)
                positiveButton(CommonString.dialog_ok)
                negativeButton(CommonString.dialog_cancel)
                setView(R.layout.dialog_generic_text_input)
            }.input(
                prefill = current,
                allowEmpty = true,
                displayKeyboard = true,
            ) { dialog, text ->
                // Blank means "use the provider's default model".
                setModel(text.toString().trim().ifBlank { null })
                dialog.dismiss()
            }
    }

    private fun providerFor(providerKind: AiProviderKind): AiProvider =
        when (providerKind) {
            AiProviderKind.OPENAI -> OpenAiProvider()
            AiProviderKind.ANTHROPIC -> AnthropicProvider()
            AiProviderKind.GEMINI -> GeminiProvider()
        }

    companion object {
        /** "Default" and "Custom…" precede the fetched models in the picker. */
        private const val FIXED_MODEL_ITEMS = 2
        private const val DETAIL_TEXT_SCALE = 0.85f
    }
}
