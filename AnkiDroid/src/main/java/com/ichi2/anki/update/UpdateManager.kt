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

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.Channel
import com.ichi2.anki.NOTIFICATION_MIN_DELAY_MS
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
    private const val NOTIFICATION_ID = 0xD20A7E

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
        val notes = formatReleaseNotes(release.body)
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
        // 새 버전 첫 실행 때 보여 줄 수 있도록 release 노트 본문을 미리 저장한다.
        stashPendingReleaseNotes(activity, release)

        val appCtx = activity.applicationContext
        val nm = NotificationManagerCompat.from(appCtx)

        val ongoing =
            NotificationCompat
                .Builder(appCtx, Channel.APP_UPDATE.id)
                .setSmallIcon(R.drawable.ic_star_notify)
                .setContentTitle(appCtx.getString(R.string.update_downloading))
                .setContentText(release.tag)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, 0, true)
        safeNotify(nm, appCtx, ongoing.build())

        // 알림 업데이트 throttle — Android는 초당 ~5건 제한이 있어 매 read마다 보내면 무시됨.
        var lastNotifyMs = 0L

        activity.launchCatchingTask {
            try {
                val uri =
                    UpdateDownloader.download(activity, release) { pct ->
                        val now = TimeManager.time.intTimeMS()
                        if (pct != null && now - lastNotifyMs >= NOTIFICATION_MIN_DELAY_MS) {
                            lastNotifyMs = now
                            ongoing.setProgress(100, (pct * 100).toInt(), false)
                            safeNotify(nm, appCtx, ongoing.build())
                        }
                    }
                if (!UpdateInstaller.canRequestInstall(activity)) {
                    nm.cancel(NOTIFICATION_ID)
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
                showCompleteNotification(appCtx, nm, uri, release)
                // 포그라운드면 바로 설치 prompt까지 띄움 — 백그라운드일 땐 알림 탭으로 진입
                if (!activity.isFinishing) UpdateInstaller.launchInstall(activity, uri)
            } catch (e: Exception) {
                Timber.w(e, "Update download failed")
                nm.cancel(NOTIFICATION_ID)
                showThemedToast(activity, R.string.update_download_failed, true)
            }
        }
    }

    private fun showCompleteNotification(
        ctx: Context,
        nm: NotificationManagerCompat,
        apkUri: Uri,
        release: GitHubRelease,
    ) {
        val installIntent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi =
            PendingIntent.getActivity(
                ctx,
                0,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val done =
            NotificationCompat
                .Builder(ctx, Channel.APP_UPDATE.id)
                .setSmallIcon(R.drawable.ic_star_notify)
                .setContentTitle(release.tag)
                .setContentText(ctx.getString(R.string.update_ready_to_install))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .addAction(0, ctx.getString(R.string.update_install_action), pi)
                .build()
        safeNotify(nm, ctx, done)
    }

    /**
     * 새 버전 첫 실행 시 한 번만 "새 버전 안내" 다이얼로그를 띄운다. 우선순위:
     *  1. 인앱 업데이터가 미리 저장해 둔 release body
     *  2. (1) 없으면 GitHub 에서 현재 태그의 body fetch (네트워크 필요)
     * dev 빌드(FORK_VERSION 비어있음)와 첫 설치(lastSeenVersion 미설정)는 노옵.
     */
    fun showReleaseNotesIfNew(activity: FragmentActivity) {
        val currentTag = BuildConfig.FORK_VERSION
        if (currentTag.isEmpty()) return

        val prefs = activity.sharedPrefs()
        val lastSeenKey = activity.getString(R.string.pref_last_seen_version_key)
        val lastSeen = prefs.getString(lastSeenKey, null)
        if (lastSeen == null) {
            // 처음 설치하는 경우엔 안내 없이 현재 태그를 stamp 만 해 둔다.
            prefs.edit { putString(lastSeenKey, currentTag) }
            return
        }
        if (lastSeen == currentTag) return

        val tagKey = activity.getString(R.string.pref_pending_release_notes_tag_key)
        val bodyKey = activity.getString(R.string.pref_pending_release_notes_body_key)
        val pendingTag = prefs.getString(tagKey, null)
        val pendingBody = prefs.getString(bodyKey, null)

        if (pendingTag == currentTag && !pendingBody.isNullOrBlank()) {
            displayWhatsNew(activity, currentTag, pendingBody)
            prefs.edit {
                remove(tagKey)
                remove(bodyKey)
                putString(lastSeenKey, currentTag)
            }
            return
        }

        // 폴백: GitHub 에서 직접 가져온다.
        activity.launchCatchingTask {
            val release = UpdateChecker.fetchReleaseByTag(currentTag)
            val body = release?.body
            if (!body.isNullOrBlank() && activity.isAdded()) {
                displayWhatsNew(activity, currentTag, body)
            }
            prefs.edit { putString(lastSeenKey, currentTag) }
        }
    }

    private fun displayWhatsNew(
        activity: FragmentActivity,
        tag: String,
        body: String,
    ) {
        AlertDialog.Builder(activity).show {
            title(text = activity.getString(R.string.whats_new_title, tag))
            message(text = formatReleaseNotes(body))
            positiveButton(R.string.whats_new_dismiss)
        }
    }

    /**
     * Strips the most common Markdown chrome from a GitHub release body so it
     * reads cleanly as plain text inside an [AlertDialog]. Keeps line breaks,
     * link URLs and the actual content; drops `**`, `##`/`#` headings, and
     * bullet markers (`*`/`-`/`+`) — replacing bullets with `•`. Inline
     * `[label](url)` becomes `label (url)`.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal fun formatReleaseNotes(body: String): String {
        if (body.isBlank()) return body
        val linkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
        val boldRegex = Regex("""\*\*(.+?)\*\*""")
        val italicRegex = Regex("""(?<![*_])[*_](?!\s)([^*_\n]+?)[*_](?![*_])""")
        return body
            .lineSequence()
            .map { line ->
                var out = line.trimEnd()
                out = out.replace(linkRegex) { "${it.groupValues[1]} (${it.groupValues[2]})" }
                out = out.replace(boldRegex) { it.groupValues[1] }
                out = out.replace(italicRegex) { it.groupValues[1] }
                out =
                    when {
                        out.startsWith("### ") -> out.removePrefix("### ")
                        out.startsWith("## ") -> out.removePrefix("## ")
                        out.startsWith("# ") -> out.removePrefix("# ")
                        else -> out
                    }
                // Bullet markers: "* ", "- ", "+ " (with optional leading indent)
                out = out.replace(Regex("""^(\s*)[*+\-]\s+"""), "$1• ")
                out
            }.joinToString("\n")
            .trim()
    }

    private fun stashPendingReleaseNotes(
        activity: FragmentActivity,
        release: GitHubRelease,
    ) {
        if (release.body.isBlank()) return
        val prefs = activity.sharedPrefs()
        prefs.edit {
            putString(activity.getString(R.string.pref_pending_release_notes_tag_key), release.tag)
            putString(activity.getString(R.string.pref_pending_release_notes_body_key), release.body)
        }
    }

    private fun FragmentActivity.isAdded() = !isFinishing && !isDestroyed

    private fun safeNotify(
        nm: NotificationManagerCompat,
        ctx: Context,
        notification: Notification,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Timber.d("Skipping notification: POST_NOTIFICATIONS not granted")
                return
            }
        }
        nm.notify(NOTIFICATION_ID, notification)
    }
}
