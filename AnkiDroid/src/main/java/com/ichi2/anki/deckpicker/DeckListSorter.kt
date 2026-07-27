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

/** Sorts normally by date. */
private const val GROUP_ACTIVE = 0

/**
 * [DeckSortOrder.LEAST_RECENT] only: studied recently enough to sort by date, but with nothing
 * left to study today, so it sits below the decks that actually need work.
 */
private const val GROUP_NOTHING_DUE = 1

/** Pinned to the bottom, keeping the list's existing order. */
private const val GROUP_PINNED = 2

/**
 * Which block of the sorted list [this] deck belongs to; blocks are laid out in ascending order.
 */
private fun DisplayDeckNode.sortGroup(
    order: DeckSortOrder,
    dayStartMillis: Long,
    staleThresholdMs: Long,
): Int {
    val ms = lastStudiedMillis ?: return GROUP_PINNED
    return when {
        order == DeckSortOrder.MOST_RECENT ->
            if ((dayStartMillis - ms) >= staleThresholdMs) GROUP_PINNED else GROUP_ACTIVE
        !hasCardsReadyToStudy -> GROUP_NOTHING_DUE
        else -> GROUP_ACTIVE
    }
}

/**
 * Re-orders [this] flat deck list according to [order], treating every deck
 * independently regardless of parent/child nesting.
 *
 * - [DeckSortOrder.NAME]: returns the list unchanged.
 * - [DeckSortOrder.LEAST_RECENT]: least-recently-studied first, but only among decks that still
 *   have cards waiting today — this order exists to reach the decks whose backlog has piled up, and
 *   a deck with nothing due needs no work no matter how long it has been idle, so it drops below
 *   them (still oldest-first among its peers). Each deck's [DisplayDeckNode.lastStudiedMillis] was
 *   already computed excluding subdecks idle 100+ days (see [aggregatedLastStudiedMillis]), so only
 *   a `null` value (nothing eligible) is pinned to the bottom.
 * - [DeckSortOrder.MOST_RECENT]: most-recently-studied first; decks idle for ≥30 days (or never
 *   studied) are pinned to the bottom.
 */
fun List<DisplayDeckNode>.sortedByStudyOrder(
    order: DeckSortOrder,
    dayStartMillis: Long,
): List<DisplayDeckNode> {
    if (order == DeckSortOrder.NAME) return this
    val staleThresholdMs = STALE_THRESHOLD_DAYS * MILLIS_PER_DAY
    return sortedWith { a, b ->
        val aGroup = a.sortGroup(order, dayStartMillis, staleThresholdMs)
        val bGroup = b.sortGroup(order, dayStartMillis, staleThresholdMs)
        when {
            aGroup != bGroup -> aGroup - bGroup
            aGroup == GROUP_PINNED -> 0
            order == DeckSortOrder.LEAST_RECENT -> compareValues(a.lastStudiedMillis, b.lastStudiedMillis)
            else -> compareValues(b.lastStudiedMillis, a.lastStudiedMillis)
        }
    }
}
