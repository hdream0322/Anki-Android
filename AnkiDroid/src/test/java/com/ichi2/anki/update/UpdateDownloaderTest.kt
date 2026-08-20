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
import java.io.File

class UpdateDownloaderTest {
    @Test
    fun `computeSha256 matches the known digest of a file's contents`() {
        val file = File.createTempFile("update-downloader-test", ".apk")
        try {
            file.writeText("hello world")
            // sha256("hello world")
            assertEquals(
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                UpdateDownloader.computeSha256(file),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `sha256Matches is case-insensitive`() {
        assertTrue(UpdateDownloader.sha256Matches(actual = "ABCDEF", expected = "abcdef"))
    }

    @Test
    fun `sha256Matches is false on mismatch`() {
        assertFalse(UpdateDownloader.sha256Matches(actual = "abcdef", expected = "123456"))
    }
}
