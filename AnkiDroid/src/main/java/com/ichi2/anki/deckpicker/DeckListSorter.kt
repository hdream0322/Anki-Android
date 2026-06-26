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

private const val STALE_THRESHOLD_DAYS = 30L
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Re-orders [this] flat deck list according to [order], treating every deck
 * independently regardless of parent/child nesting.
 *
 * - [DeckSortOrder.NAME]: returns the list unchanged.
 * - [DeckSortOrder.LEAST_RECENT]: least-recently-studied first; decks idle for
 *   ≥30 days (or never studied) are pinned to the bottom.
 * - [DeckSortOrder.MOST_RECENT]: most-recently-studied first; same stale-pinning
 *   behaviour.
 */
fun List<DisplayDeckNode>.sortedByStudyOrder(
    order: DeckSortOrder,
    dayStartMillis: Long,
): List<DisplayDeckNode> {
    if (order == DeckSortOrder.NAME) return this
    val staleThresholdMs = STALE_THRESHOLD_DAYS * MILLIS_PER_DAY
    return sortedWith { a, b ->
        val aMs = a.lastStudiedMillis
        val bMs = b.lastStudiedMillis
        val aStale = aMs == null || (dayStartMillis - aMs) >= staleThresholdMs
        val bStale = bMs == null || (dayStartMillis - bMs) >= staleThresholdMs
        when {
            aStale && bStale -> 0
            aStale -> 1
            bStale -> -1
            order == DeckSortOrder.LEAST_RECENT -> compareValues(aMs, bMs)
            else -> compareValues(bMs, aMs)
        }
    }
}
