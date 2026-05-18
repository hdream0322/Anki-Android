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

import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Maximum number of days for which the relative `"Nd"` form is shown. Older entries fall back to an
 * absolute ISO date so the number doesn't grow unbounded.
 */
private const val RELATIVE_DAYS_THRESHOLD = 30L

/**
 * The last time each deck was studied, keyed by deck id, as epoch milliseconds.
 *
 * Anki's `revlog` table does not store a deck id, so this joins `revlog` against `cards` and uses
 * `revlog.id` (which *is* the review timestamp in epoch ms). Cards temporarily pulled into a
 * filtered deck (`odid != 0`) are attributed back to their home deck so the value stays with the
 * real deck once the card is returned.
 *
 * The returned map only contains decks that have at least one review; callers treat a missing key
 * as "never studied".
 */
fun Collection.lastReviewMillisByDeck(): Map<DeckId, Long> {
    val map = HashMap<DeckId, Long>()
    db
        .query(
            "SELECT CASE WHEN c.odid != 0 THEN c.odid ELSE c.did END AS deck, " +
                "MAX(r.id) FROM revlog r JOIN cards c ON r.cid = c.id GROUP BY deck",
        ).use { cur ->
            while (cur.moveToNext()) {
                map[cur.getLong(0)] = cur.getLong(1)
            }
        }
    return map
}

/**
 * Formats a deck's last-studied time for the compact column shown left of the card counts.
 *
 * - `null` (never studied) -> [neverLabel] (default `"-"`)
 * - today (or a future timestamp, defended) -> [todayLabel] (default `"Today"`)
 * - within [RELATIVE_DAYS_THRESHOLD] days -> [daysAgo] (default `"Nd"`, e.g. `3d`, `29d`)
 * - older -> ISO local date (e.g. `2026-01-15`), intentionally kept as an absolute date
 *
 * The user-facing labels are injected so this stays a pure, Context-free function (the deck
 * list passes localized strings from resources). Uses the device-local calendar day; this may
 * differ from Anki's day-rollover by up to the rollover offset, acceptable at a glance.
 *
 * [now] and [zone] are injectable for testing.
 */
fun formatLastStudied(
    lastStudiedMillis: Long?,
    now: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    neverLabel: String = "-",
    todayLabel: String = "Today",
    daysAgo: (Long) -> String = { "${it}d" },
): String {
    if (lastStudiedMillis == null) return neverLabel
    val date = Instant.ofEpochMilli(lastStudiedMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, now)
    return when {
        days <= 0L -> todayLabel // today, or a future timestamp defended as "today"
        days <= RELATIVE_DAYS_THRESHOLD -> daysAgo(days)
        else -> date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
