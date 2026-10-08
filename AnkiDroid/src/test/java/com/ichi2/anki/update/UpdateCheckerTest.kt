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
package com.ichi2.anki.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNull

class UpdateCheckerTest {
    @Test
    fun `parseRelease extracts the apk sha256 digest from the matching asset`() {
        val json =
            """
            {
              "tag_name": "v0.0.21",
              "name": "v0.0.21",
              "body": "notes",
              "assets": [
                {
                  "name": "AnkiDroid-v0.0.21.apk",
                  "browser_download_url": "https://example.com/AnkiDroid-v0.0.21.apk",
                  "digest": "sha256:ABCDEF0123456789"
                }
              ]
            }
            """.trimIndent()
        val release = UpdateChecker.parseRelease(json, supportedAbis = listOf("arm64-v8a"))
        assertEquals("abcdef0123456789", release?.apkSha256)
    }

    @Test
    fun `parseRelease apkSha256 is null when the asset has no digest`() {
        val json =
            """
            {
              "tag_name": "v0.0.21",
              "name": "v0.0.21",
              "body": "notes",
              "assets": [
                {
                  "name": "AnkiDroid-v0.0.21.apk",
                  "browser_download_url": "https://example.com/AnkiDroid-v0.0.21.apk"
                }
              ]
            }
            """.trimIndent()
        val release = UpdateChecker.parseRelease(json, supportedAbis = listOf("arm64-v8a"))
        assertNull(release?.apkSha256)
    }

    private fun splitReleaseJson(vararg abis: String): String {
        val assets =
            abis.joinToString(",") { abi ->
                """{"name": "AnkiDroid-deurim-v0.1.11-$abi.apk", "browser_download_url": "https://example.com/$abi.apk"}"""
            }
        return """{"tag_name": "v0.1.11", "name": "v0.1.11", "body": "", "assets": [$assets]}"""
    }

    private val allAssets = arrayOf("arm64-v8a", "armeabi-v7a", "universal", "x86", "x86_64")

    @Test
    fun `parseRelease picks the apk for the device's primary abi`() {
        val release = UpdateChecker.parseRelease(splitReleaseJson(*allAssets), supportedAbis = listOf("armeabi-v7a", "armeabi"))
        assertEquals("AnkiDroid-deurim-v0.1.11-armeabi-v7a.apk", release?.apkName)
        assertEquals("https://example.com/armeabi-v7a.apk", release?.apkUrl)
    }

    @Test
    fun `parseRelease does not confuse x86 with x86_64`() {
        val release = UpdateChecker.parseRelease(splitReleaseJson(*allAssets), supportedAbis = listOf("x86"))
        assertEquals("AnkiDroid-deurim-v0.1.11-x86.apk", release?.apkName)
    }

    @Test
    fun `parseRelease prefers abis in the device's order`() {
        val release = UpdateChecker.parseRelease(splitReleaseJson(*allAssets), supportedAbis = listOf("x86_64", "x86", "arm64-v8a"))
        assertEquals("AnkiDroid-deurim-v0.1.11-x86_64.apk", release?.apkName)
    }

    @Test
    fun `parseRelease falls back to a secondary abi when the primary has no apk`() {
        val release =
            UpdateChecker.parseRelease(
                splitReleaseJson("armeabi-v7a", "universal"),
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            )
        assertEquals("AnkiDroid-deurim-v0.1.11-armeabi-v7a.apk", release?.apkName)
    }

    @Test
    fun `parseRelease falls back to the universal apk when no abi matches`() {
        val release = UpdateChecker.parseRelease(splitReleaseJson(*allAssets), supportedAbis = listOf("riscv64"))
        assertEquals("AnkiDroid-deurim-v0.1.11-universal.apk", release?.apkName)
    }

    @Test
    fun `extractSha256Digest strips the sha256 prefix and lowercases the hex`() {
        assertEquals("abcdef0123456789", UpdateChecker.extractSha256Digest("sha256:ABCDEF0123456789"))
    }

    @Test
    fun `extractSha256Digest returns null for a non-sha256 digest`() {
        assertNull(UpdateChecker.extractSha256Digest("sha1:abcdef"))
    }

    @Test
    fun `extractSha256Digest returns null for a null digest`() {
        assertNull(UpdateChecker.extractSha256Digest(null))
    }

    @Test
    fun `isSkipped is true when latest tag matches the skipped tag`() {
        assertTrue(UpdateChecker.isSkipped(latestTag = "v0.0.21", skippedTag = "v0.0.21"))
    }

    @Test
    fun `isSkipped is false when latest tag differs from the skipped tag`() {
        assertFalse(UpdateChecker.isSkipped(latestTag = "v0.0.22", skippedTag = "v0.0.21"))
    }

    @Test
    fun `isSkipped is false when nothing was skipped`() {
        assertFalse(UpdateChecker.isSkipped(latestTag = "v0.0.21", skippedTag = null))
    }

    @Test
    fun `isNewer returns true when latest tag has a higher patch version`() {
        assertTrue(UpdateChecker.isNewer(latestTag = "v0.0.21", currentTag = "v0.0.20"))
    }

    @Test
    fun `isNewer returns false when tags are equal`() {
        assertFalse(UpdateChecker.isNewer(latestTag = "v0.0.20", currentTag = "v0.0.20"))
    }
}
