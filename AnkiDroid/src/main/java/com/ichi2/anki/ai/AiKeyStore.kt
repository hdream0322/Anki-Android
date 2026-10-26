// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("DEPRECATION")

package com.ichi2.anki.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AiProviderKind
import timber.log.Timber

/**
 * Stores one LLM API key per provider, encrypted-at-rest, separate from ordinary [Prefs].
 *
 * @param legacyKeyOwner the provider a key saved before keys were per-provider belongs to
 */
class AiKeyStore(
    context: Context,
    private val prefs: SharedPreferences = buildEncryptedPrefs(context),
    legacyKeyOwner: () -> AiProviderKind = { Prefs.aiProviderKind },
) {
    init {
        // Only one key used to exist; it was for whichever provider was selected.
        prefs.getString(PREF_LEGACY_API_KEY, null)?.let { legacyKey ->
            val owner = legacyKeyOwner()
            prefs.edit {
                if (!prefs.contains(keyFor(owner))) putString(keyFor(owner), legacyKey)
                remove(PREF_LEGACY_API_KEY)
            }
        }
    }

    fun getApiKey(provider: AiProviderKind): String? = prefs.getString(keyFor(provider), null)

    fun setApiKey(
        provider: AiProviderKind,
        value: String?,
    ) = prefs.edit { putString(keyFor(provider), value) }

    fun hasApiKey(provider: AiProviderKind): Boolean = !getApiKey(provider).isNullOrBlank()

    companion object {
        private const val FILE_NAME = "ai_key_store"
        private const val PREF_LEGACY_API_KEY = "api_key"

        private fun keyFor(provider: AiProviderKind) = "api_key_${provider.name.lowercase()}"

        private fun buildEncryptedPrefs(context: Context): SharedPreferences =
            try {
                createEncryptedPrefs(context)
            } catch (e: Exception) {
                // The master key lives in AndroidKeyStore and is device-bound, so a prefs file
                // arriving from a backup or device transfer can never be decrypted here.
                Timber.w(e, "Could not open the AI key store; recreating it")
                context.deleteSharedPreferences(FILE_NAME)
                createEncryptedPrefs(context)
            }

        private fun createEncryptedPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }
}
