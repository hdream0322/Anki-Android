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

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import com.ichi2.anki.web.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

object UpdateDownloader {
    /**
     * Downloads [release].apkUrl to the app cache and returns a FileProvider URI
     * suitable for handing to the system installer.
     *
     * [onProgress] is invoked on the calling coroutine's thread with values in [0f, 1f];
     * `null` percent means the total size was unknown (rare for GitHub asset CDN).
     */
    suspend fun download(
        context: Context,
        release: GitHubRelease,
        onProgress: (Float?) -> Unit = {},
    ): Uri =
        withContext(Dispatchers.IO) {
            val targetDir = File(context.cacheDir, "updates").apply { mkdirs() }
            // 같은 태그를 다시 받으면 덮어쓰도록 파일명에 태그 포함
            val targetFile = File(targetDir, "${release.tag}-${release.apkName}")
            // 이전에 받아둔 APK들이 캐시에 쌓이지 않도록 정리
            targetDir.listFiles()?.forEach { stale ->
                if (stale != targetFile && !stale.delete()) {
                    Timber.w("Failed to delete stale update file: %s", stale.name)
                }
            }
            val client = HttpFetcher.getOkHttpBuilder(fakeUserAgent = false).build()
            val request = Request.Builder().url(release.apkUrl).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed: HTTP ${response.code}")
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(total?.let { downloaded.toFloat() / it.toFloat() })
                        }
                    }
                }
            }
            Timber.i("Downloaded %s (%d bytes)", targetFile.name, targetFile.length())

            release.apkSha256?.let { expected ->
                val actual = computeSha256(targetFile)
                if (!sha256Matches(actual, expected)) {
                    targetFile.delete()
                    error("Checksum mismatch for ${targetFile.name}: expected $expected but got $actual")
                }
                Timber.i("Verified SHA-256 checksum for %s", targetFile.name)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.apkgfileprovider",
                targetFile,
            )
        }

    @VisibleForTesting
    internal fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @VisibleForTesting
    internal fun sha256Matches(
        actual: String,
        expected: String,
    ): Boolean = actual.equals(expected, ignoreCase = true)
}
