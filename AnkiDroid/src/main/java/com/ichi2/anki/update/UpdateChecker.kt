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

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.web.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

object UpdateChecker {
    private const val RELEASES_API_URL =
        "https://api.github.com/repos/hdream0322/Anki-Android/releases/latest"
    private const val RELEASE_BY_TAG_URL =
        "https://api.github.com/repos/hdream0322/Anki-Android/releases/tags/%s"

    suspend fun fetchLatestRelease(): GitHubRelease? = fetchRelease(RELEASES_API_URL)

    /** Look up a specific tag — used to show release notes for the version the user is now on. */
    suspend fun fetchReleaseByTag(tag: String): GitHubRelease? = fetchRelease(RELEASE_BY_TAG_URL.format(tag))

    private suspend fun fetchRelease(url: String): GitHubRelease? =
        withContext(Dispatchers.IO) {
            val client = HttpFetcher.getOkHttpBuilder(fakeUserAgent = false).build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "AnkiDroid-Deurim-Updater")
                    .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("UpdateChecker: HTTP %d on %s", response.code, url)
                        return@use null
                    }
                    parseRelease(response.body.string())
                }
            } catch (e: Exception) {
                Timber.w(e, "UpdateChecker: fetch failed for %s", url)
                null
            }
        }

    @VisibleForTesting
    fun parseRelease(json: String): GitHubRelease? {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").takeIf { it.isNotEmpty() } ?: return null
        var apkUrl = ""
        var apkName = ""
        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                    apkUrl = url
                    apkName = name
                    break
                }
            }
        }
        return GitHubRelease(
            tag = tag,
            name = obj.optString("name", tag),
            body = obj.optString("body"),
            apkUrl = apkUrl,
            apkName = apkName,
        )
    }

    /**
     * Returns true if [latestTag] is strictly newer than the current fork build.
     * Both are expected to follow `vMAJOR.MINOR.PATCH`. Returns false for local
     * dev builds (`BuildConfig.FORK_VERSION` empty) so the check is a no-op.
     */
    fun isNewerThanCurrent(latestTag: String): Boolean = isNewer(latestTag, BuildConfig.FORK_VERSION)

    @VisibleForTesting
    fun isNewer(
        latestTag: String,
        currentTag: String,
    ): Boolean {
        val current = parseSemver(currentTag) ?: return false
        val latest = parseSemver(latestTag) ?: return false
        return compareSemver(latest, current) > 0
    }

    private fun parseSemver(tag: String): Triple<Int, Int, Int>? {
        val stripped = tag.removePrefix("v")
        val parts = stripped.split(".")
        if (parts.size != 3) return null
        return try {
            Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun compareSemver(
        a: Triple<Int, Int, Int>,
        b: Triple<Int, Int, Int>,
    ): Int {
        if (a.first != b.first) return a.first - b.first
        if (a.second != b.second) return a.second - b.second
        return a.third - b.third
    }
}
