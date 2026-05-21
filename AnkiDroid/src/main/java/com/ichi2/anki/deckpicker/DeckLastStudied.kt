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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Maximum number of days for which the relative `"Nd"` form is shown. Older entries fall back to an
 * absolute ISO date so the number doesn't grow unbounded.
 */
private const val RELATIVE_DAYS_THRESHOLD = 30L

private const val MILLIS_PER_DAY = 86_400_000L

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
 * Day boundaries follow Anki's rollover (default 4 AM, configurable), supplied via
 * [dayStartMillis] — the epoch-millis at which the *current* Anki day began. Reviews at or after
 * that instant are "today"; earlier reviews bucket into N-day-ago slots aligned to the same
 * rollover hour, so a 03:00 review still counts as the previous Anki day.
 *
 * - `null` (never studied) -> [neverLabel] (default `"-"`)
 * - today (or a future timestamp, defended) -> [todayLabel] (default `"Today"`)
 * - within [RELATIVE_DAYS_THRESHOLD] days -> [daysAgo] (default `"Nd"`, e.g. `3d`, `29d`)
 * - older -> ISO local date for the Anki day of the review (e.g. `2026-01-15`)
 */
fun formatLastStudied(
    lastStudiedMillis: Long?,
    dayStartMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    neverLabel: String = "-",
    todayLabel: String = "Today",
    daysAgo: (Long) -> String = { "${it}d" },
): String {
    if (lastStudiedMillis == null) return neverLabel
    val diff = dayStartMillis - lastStudiedMillis
    if (diff <= 0L) return todayLabel
    // ceil: a review 1ms before today's rollover is "1d ago", one full day earlier is also "1d"
    val days = (diff + MILLIS_PER_DAY - 1L) / MILLIS_PER_DAY
    if (days <= RELATIVE_DAYS_THRESHOLD) return daysAgo(days)
    val dayStart = Instant.ofEpochMilli(dayStartMillis - days * MILLIS_PER_DAY).atZone(zone).toLocalDate()
    return dayStart.format(DateTimeFormatter.ISO_LOCAL_DATE)
}
