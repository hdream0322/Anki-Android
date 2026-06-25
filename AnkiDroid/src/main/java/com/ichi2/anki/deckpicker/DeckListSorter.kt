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
 * Intermediate tree node used during sort — bundles a [DisplayDeckNode] with its
 * already-flattened children so we can sort siblings independently at every depth level.
 */
private data class DeckGroup(
    val node: DisplayDeckNode,
    val children: List<DeckGroup>,
)

/**
 * Parses a flat, depth-ordered [DisplayDeckNode] list (as returned by
 * [DeckNode.filterAndFlattenDisplay]) into a list of [DeckGroup] trees.
 *
 * Items whose [DisplayDeckNode.depth] exceeds [parentDepth] are treated as children
 * of the most-recent item at [parentDepth].
 */
private fun List<DisplayDeckNode>.toGroups(parentDepth: Int = -1): List<DeckGroup> {
    val result = mutableListOf<DeckGroup>()
    var i = 0
    while (i < size) {
        val node = this[i]
        if (node.depth <= parentDepth) break
        val descendants = mutableListOf<DisplayDeckNode>()
        var j = i + 1
        while (j < size && this[j].depth > node.depth) {
            descendants.add(this[j])
            j++
        }
        result.add(DeckGroup(node, descendants.toGroups(node.depth)))
        i = j
    }
    return result
}

private fun List<DeckGroup>.flatten(): List<DisplayDeckNode> {
    val out = mutableListOf<DisplayDeckNode>()
    forEach { group ->
        out.add(group.node)
        out.addAll(group.children.flatten())
    }
    return out
}

private fun List<DeckGroup>.sortedByOrder(
    order: DeckSortOrder,
    dayStartMillis: Long,
    staleThresholdMs: Long,
): List<DeckGroup> {
    val withSortedChildren =
        map { group ->
            group.copy(children = group.children.sortedByOrder(order, dayStartMillis, staleThresholdMs))
        }
    return withSortedChildren.sortedWith { a, b ->
        val aMs = a.node.lastStudiedMillis
        val bMs = b.node.lastStudiedMillis
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

/**
 * Re-orders [this] flat deck list according to [order] while preserving the
 * parent → child nesting structure.
 *
 * - [DeckSortOrder.NAME]: returns the list unchanged.
 * - [DeckSortOrder.LEAST_RECENT]: least-recently-studied siblings first; decks
 *   with no review in the past [STALE_THRESHOLD_DAYS] days (or never studied)
 *   are pinned to the bottom of their sibling group.
 * - [DeckSortOrder.MOST_RECENT]: most-recently-studied siblings first; same
 *   stale-pinning behaviour.
 *
 * Each parent's [DisplayDeckNode.lastStudiedMillis] already reflects the max across
 * itself and all descendants (set in [DisplayDeckNode.from]), so parent ordering
 * naturally "absorbs" child study times.
 */
fun List<DisplayDeckNode>.sortedByStudyOrder(
    order: DeckSortOrder,
    dayStartMillis: Long,
): List<DisplayDeckNode> {
    if (order == DeckSortOrder.NAME) return this
    val staleThresholdMs = STALE_THRESHOLD_DAYS * MILLIS_PER_DAY
    return toGroups()
        .sortedByOrder(order, dayStartMillis, staleThresholdMs)
        .flatten()
}
