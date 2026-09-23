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
package com.ichi2.anki.reviewer

import com.ichi2.anki.reviewer.ProgressGlowDrawable.Companion.glowOffset
import com.ichi2.anki.reviewer.ProgressGlowDrawable.Companion.sweepFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressGlowDrawableTest {
    @Test
    fun `glow starts fully before the bar and ends fully past the fill`() {
        assertEquals(-50f, glowOffset(sweep = 0f, fillWidth = 400f, glowWidth = 50f), 0.001f)
        assertEquals(400f, glowOffset(sweep = 1f, fillWidth = 400f, glowWidth = 50f), 0.001f)
    }

    @Test
    fun `sweep moves forward then pauses`() {
        val early = sweepFraction(0.1f)
        val late = sweepFraction(0.5f)
        assertNotNull(early)
        assertNotNull(late)
        assertTrue(early!! < late!!)
        assertEquals(0f, sweepFraction(0f)!!, 0.001f)
        assertNull("no glow during the pause", sweepFraction(0.8f))
    }
}
