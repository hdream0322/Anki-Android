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
 *  this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.update

import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

object UpdateManager {
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /**
     * Auto-check entry point called from [com.ichi2.anki.DeckPicker] startup.
     * No-op on dev builds, when the user disabled the toggle, or when checked within 24h.
     */
    fun checkAndPromptIfDue(activity: FragmentActivity) {
        if (BuildConfig.FORK_VERSION.isEmpty()) {
            Timber.d("Update check skipped: dev build (FORK_VERSION empty)")
            return
        }
        val prefs = activity.sharedPrefs()
        val autoKey = activity.getString(R.string.pref_auto_update_check_key)
        if (!prefs.getBoolean(autoKey, true)) return
        val lastKey = activity.getString(R.string.pref_last_update_check_key)
        val last = prefs.getLong(lastKey, 0L)
        if (TimeManager.time.intTimeMS() - last < CHECK_INTERVAL_MS) {
            Timber.d("Update check skipped: checked recently")
            return
        }
        runCheck(activity, manual = false)
    }

    /** About 화면 등에서 호출 — 쿨다운 무시, 결과는 항상 사용자에게 피드백. */
    fun checkNow(activity: FragmentActivity) {
        runCheck(activity, manual = true)
    }

    private fun runCheck(
        activity: FragmentActivity,
        manual: Boolean,
    ) {
        activity.launchCatchingTask {
            val release = UpdateChecker.fetchLatestRelease()
            stampCheck(activity)
            if (release == null) {
                if (manual) showThemedToast(activity, R.string.update_check_failed, true)
                return@launchCatchingTask
            }
            if (!UpdateChecker.isNewerThanCurrent(release.tag)) {
                if (manual) showThemedToast(activity, R.string.update_already_latest, true)
                return@launchCatchingTask
            }
            promptUpdate(activity, release)
        }
    }

    private fun stampCheck(activity: FragmentActivity) {
        val key = activity.getString(R.string.pref_last_update_check_key)
        activity.sharedPrefs().edit { putLong(key, TimeManager.time.intTimeMS()) }
    }

    private fun promptUpdate(
        activity: FragmentActivity,
        release: GitHubRelease,
    ) {
        val current = BuildConfig.FORK_VERSION.ifEmpty { "(dev)" }
        val notes =
            release.body
                .lineSequence()
                .take(8)
                .joinToString("\n")
        AlertDialog.Builder(activity).show {
            title(R.string.update_available_title)
            message(text = activity.getString(R.string.update_available_message, current, release.tag, notes))
            positiveButton(R.string.update_install_now) { startDownload(activity, release) }
            negativeButton(R.string.update_later)
        }
    }

    private fun startDownload(
        activity: FragmentActivity,
        release: GitHubRelease,
    ) {
        val progressBar =
            LinearProgressIndicator(activity).apply {
                isIndeterminate = true
                max = 100
            }
        val container =
            FrameLayout(activity).apply {
                val pad = (24 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(progressBar)
            }
        val progressDialog =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.update_downloading)
                .setView(container)
                .setCancelable(false)
                .show()
        activity.launchCatchingTask {
            try {
                val uri =
                    UpdateDownloader.download(activity, release) { pct ->
                        activity.runOnUiThread {
                            if (pct != null) {
                                progressBar.isIndeterminate = false
                                progressBar.setProgressCompat((pct * 100).toInt(), true)
                            }
                        }
                    }
                progressDialog.dismiss()
                if (!UpdateInstaller.canRequestInstall(activity)) {
                    AlertDialog.Builder(activity).show {
                        title(R.string.update_available_title)
                        message(R.string.update_need_install_permission)
                        positiveButton(R.string.update_open_settings) {
                            UpdateInstaller.openUnknownAppSourcesSettings(activity)
                        }
                        negativeButton(R.string.update_later)
                    }
                    return@launchCatchingTask
                }
                UpdateInstaller.launchInstall(activity, uri)
            } catch (e: Exception) {
                Timber.w(e, "Update download failed")
                progressDialog.dismiss()
                showThemedToast(activity, R.string.update_download_failed, true)
            }
        }
    }
}
