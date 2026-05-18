/*
 *  Copyright (c) 2026 AnkiDroid (deurim fork)
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

package com.ichi2.anki.deckpicker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DeckLastStudiedTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 5, 18)

    /** Epoch millis for [date] at start of day in [zone]. */
    private fun millis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `never studied shows dash`() {
        assertEquals("-", formatLastStudied(null, today, zone))
    }

    @Test
    fun `studied today shows today label`() {
        assertEquals("Today", formatLastStudied(millis(today), today, zone))
    }

    @Test
    fun `studied yesterday shows 1d`() {
        assertEquals("1d", formatLastStudied(millis(today.minusDays(1)), today, zone))
    }

    @Test
    fun `30 days ago is still relative`() {
        assertEquals("30d", formatLastStudied(millis(today.minusDays(30)), today, zone))
    }

    @Test
    fun `31 days ago falls back to ISO date`() {
        val date = today.minusDays(31)
        assertEquals(date.toString(), formatLastStudied(millis(date), today, zone))
        // sanity: ISO_LOCAL_DATE form
        assertEquals("2026-04-17", formatLastStudied(millis(date), today, zone))
    }

    @Test
    fun `future timestamp is defended as today`() {
        assertEquals("Today", formatLastStudied(millis(today.plusDays(3)), today, zone))
    }

    @Test
    fun `timezone is honoured`() {
        // A review at 23:00 UTC on the 17th is "yesterday" in UTC but "today" in UTC+2
        val reviewUtc =
            LocalDate
                .of(2026, 5, 17)
                .atTime(23, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        assertEquals("1d", formatLastStudied(reviewUtc, today, ZoneId.of("UTC")))
        assertEquals("Today", formatLastStudied(reviewUtc, today, ZoneOffset.ofHours(2)))
    }
}
