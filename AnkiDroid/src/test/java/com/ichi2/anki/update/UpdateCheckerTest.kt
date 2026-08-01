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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
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
