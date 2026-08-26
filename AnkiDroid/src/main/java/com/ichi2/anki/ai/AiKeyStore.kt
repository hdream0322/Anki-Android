// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("DEPRECATION")

package com.ichi2.anki.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

/** Stores the user's LLM API key encrypted-at-rest, separate from ordinary [com.ichi2.anki.settings.Prefs]. */
class AiKeyStore(
    context: Context,
    private val prefs: SharedPreferences = buildEncryptedPrefs(context),
) {
    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()

    companion object {
        private const val FILE_NAME = "ai_key_store"
        private const val KEY_API_KEY = "api_key"

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
