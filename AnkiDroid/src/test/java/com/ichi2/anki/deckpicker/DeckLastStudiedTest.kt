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
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class DeckLastStudiedTest {
    private val zone = ZoneId.of("UTC")

    // The current Anki day starts on 2026-05-18 at 04:00 UTC (default rollover).
    private val rollover: LocalTime = LocalTime.of(4, 0)
    private val today: LocalDate = LocalDate.of(2026, 5, 18)
    private val dayStart: Long =
        today.atTime(rollover).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Epoch ms for [date] at the rollover hour in UTC. */
    private fun dayStartOf(date: LocalDate): Long = date.atTime(rollover).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `never studied shows dash`() {
        assertEquals("-", formatLastStudied(null, dayStart, zone))
    }

    @Test
    fun `studied today shows today label`() {
        // a review at noon today
        val noonToday = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("Today", formatLastStudied(noonToday, dayStart, zone))
    }

    @Test
    fun `studied yesterday shows 1d`() {
        val noonYesterday =
            today
                .minusDays(1)
                .atTime(12, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        assertEquals("1d", formatLastStudied(noonYesterday, dayStart, zone))
    }

    @Test
    fun `30 days ago is still relative`() {
        val ts =
            today
                .minusDays(30)
                .atTime(12, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        assertEquals("30d", formatLastStudied(ts, dayStart, zone))
    }

    @Test
    fun `31 days ago falls back to ISO date`() {
        val date = today.minusDays(31)
        val ts = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("2026-04-17", formatLastStudied(ts, dayStart, zone))
    }

    @Test
    fun `future timestamp is defended as today`() {
        val ts =
            today
                .plusDays(3)
                .atTime(12, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        assertEquals("Today", formatLastStudied(ts, dayStart, zone))
    }

    @Test
    fun `review at 3am before rollover counts as previous day`() {
        // Anki day starts at 04:00 — a review at 03:00 belongs to the previous day.
        val ts = today.atTime(3, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("1d", formatLastStudied(ts, dayStart, zone))
    }

    @Test
    fun `review at exact rollover is today`() {
        // Review at exactly the rollover instant is part of the new day.
        assertEquals("Today", formatLastStudied(dayStart, dayStart, zone))
    }

    @Test
    fun `review one ms before rollover is 1d`() {
        assertEquals("1d", formatLastStudied(dayStart - 1L, dayStart, zone))
    }

    @Test
    fun `ISO fallback uses Anki day of review, not wall date`() {
        // Review at 03:00 on 2026-04-16 (UTC) belongs to the Anki day starting 2026-04-15 04:00.
        val ts =
            LocalDate
                .of(2026, 4, 16)
                .atTime(3, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        // That is 33 days before "today" (Anki day 2026-05-18), past the relative threshold.
        assertEquals("2026-04-15", formatLastStudied(ts, dayStart, zone))
    }
}
