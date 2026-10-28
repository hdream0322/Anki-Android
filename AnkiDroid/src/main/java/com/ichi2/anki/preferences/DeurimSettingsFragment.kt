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

import android.os.Build
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
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
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AiProviderKind
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.whiteboard.showColorPickerDialog
import com.ichi2.anki.update.UpdateManager
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ichi2.anki.common.android.R as CommonR

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
        val keyPref = requirePreference<EditTextPreference>(R.string.pref_ai_api_key_key)
        keyPref.apply {
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
            // Each provider has its own key; this one preference shows and replaces the selected provider's.
            summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    val providerKind = Prefs.aiProviderKind
                    if (keyStore.hasApiKey(providerKind)) {
                        getString(R.string.pref_ai_api_key_summary_set, providerLabel(providerKind))
                    } else {
                        getString(R.string.pref_ai_api_key_summary_not_set, providerLabel(providerKind))
                    }
                }
            setOnPreferenceChangeListener { preference, newValue ->
                val trimmed = (newValue as String).trim()
                // An empty submission must not clear an existing key — only a non-blank entry
                // replaces it.
                if (trimmed.isNotEmpty()) {
                    keyStore.setApiKey(Prefs.aiProviderKind, trimmed)
                }
                (preference as EditTextPreference).text = null
                true
            }
        }
        refreshAiApiKeySummary = { keyPref.summaryProvider = keyPref.summaryProvider }
    }

    /** Re-evaluates the API key summary, which names the selected provider. No-op until the key preference is set up. */
    private var refreshAiApiKeySummary: () -> Unit = {}

    private fun providerLabel(providerKind: AiProviderKind): String =
        resources.getStringArray(R.array.ai_provider_labels)[
            resources.getStringArray(R.array.ai_provider_values).indexOf(getString(providerKind.entryResId)),
        ]

    private fun initAiModelPref() {
        val modelPref = requirePreference<Preference>(R.string.pref_ai_model_key)
        val modelSummaryProvider =
            Preference.SummaryProvider<Preference> {
                val override = Prefs.getAiModelOverride(Prefs.aiProviderKind)
                if (override.isNullOrBlank()) {
                    getString(R.string.pref_ai_model_summary_not_set, providerFor(Prefs.aiProviderKind).defaultModel)
                } else {
                    getString(R.string.pref_ai_model_summary_set, override)
                }
            }
        // Re-setting the same SummaryProvider is the public-API way to force Preference to
        // re-evaluate it (notifyChanged() itself is protected).
        val setModel = { model: String? ->
            Prefs.setAiModelOverride(Prefs.aiProviderKind, model)
            modelPref.summaryProvider = modelSummaryProvider
        }
        modelPref.apply {
            summaryProvider = modelSummaryProvider
            setOnPreferenceClickListener {
                launchCatchingTask { showModelPicker(setModel) }
                true
            }
        }

        // Each provider keeps its own model, so switching provider only changes which one is shown,
        // as it does for the API key. The new provider is persisted only after this listener
        // returns, so the summaries refresh once it has been.
        requirePreference<ListPreference>(R.string.pref_ai_provider_key).setOnPreferenceChangeListener { _, _ ->
            listView.post {
                modelPref.summaryProvider = modelSummaryProvider
                refreshAiApiKeySummary()
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
        val apiKey = if (isRunningAsUnitTest) null else withContext(Dispatchers.IO) { AiKeyStore(context).getApiKey(Prefs.aiProviderKind) }
        val fetchedModels: List<AiModelInfo> =
            if (apiKey.isNullOrBlank()) {
                showSnackbar(R.string.ai_model_list_no_key)
                emptyList()
            } else {
                try {
                    withProgress(R.string.ai_model_list_loading) { AiModelLister().listModels(provider, apiKey) }
                } catch (e: AiError) {
                    Timber.w(e, "Could not fetch the AI model list")
                    showSnackbar(R.string.ai_model_list_failed)
                    emptyList()
                }
            }

        val current = Prefs.getAiModelOverride(Prefs.aiProviderKind)
        val fetchedIds = fetchedModels.map { it.id }
        val labels =
            listOf<CharSequence>(
                getString(R.string.ai_model_item_default, provider.defaultModel),
                getString(R.string.ai_model_item_custom),
            ) + fetchedModels.map { requireContext().aiModelRowLabel(it) }
        val checkedIndex =
            when {
                current.isNullOrBlank() -> 0
                current in fetchedIds -> FIXED_MODEL_ITEMS + fetchedIds.indexOf(current)
                else -> 1
            }
        val picker =
            AlertDialog.Builder(requireContext()).show {
                title(if (fetchedModels.isEmpty()) R.string.pref_ai_model_title else R.string.ai_model_picker_title_with_hint)
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
        picker.listView.setOnItemLongClickListener { _, _, position, _ ->
            val model = fetchedModels.getOrNull(position - FIXED_MODEL_ITEMS) ?: return@setOnItemLongClickListener false
            showModelDetails(provider, model) { picker.dismiss() }
            true
        }
    }

    /** Explains one model: cost, quality and typical use from its tier, plus every fact the vendor reported. */
    private fun showModelDetails(
        provider: AiProvider,
        model: AiModelInfo,
        onChosen: () -> Unit,
    ) {
        AlertDialog.Builder(requireContext()).show {
            setTitle(model.displayName ?: model.id)
            setMessage(requireContext().aiModelDetails(model))
            positiveButton(R.string.ai_model_detail_choose) {
                onChosen()
                Prefs.setAiModelOverride(Prefs.aiProviderKind, model.id)
                // Refreshes the summary the same way setModel does.
                requirePreference<Preference>(R.string.pref_ai_model_key).let { it.summaryProvider = it.summaryProvider }
            }
            neutralButton(R.string.ai_model_detail_pricing) { openUrl(provider.pricingUrl.toUri()) }
            negativeButton(CommonString.close)
        }
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
                setView(CommonR.layout.dialog_generic_text_input)
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
    }
}
