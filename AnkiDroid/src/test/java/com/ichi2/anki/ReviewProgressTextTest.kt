/*
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

package com.ichi2.anki

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewProgressTextTest {
    @Test
    fun `shows reviewed over total with percentage`() {
        assertEquals("30/50 (60%)", reviewProgressText(reviewed = 30, total = 50))
    }

    @Test
    fun `percentage rounds down so 100 percent only when finished`() {
        assertEquals("199/200 (99%)", reviewProgressText(reviewed = 199, total = 200))
        assertEquals("200/200 (100%)", reviewProgressText(reviewed = 200, total = 200))
    }

    @Test
    fun `zero total shows zero percent`() {
        assertEquals("0/0 (0%)", reviewProgressText(reviewed = 0, total = 0))
    }
}
