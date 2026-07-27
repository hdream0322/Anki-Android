/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2025 Gautam Bhetanabhotla <gautamarcturus@gmail.com>
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

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.libanki.utils.append

/**
 * When aggregating [DeckSortOrder.LEAST_RECENT]'s "oldest studied" date across a deck and its
 * subdecks, a subdeck idle for this long is treated as not-yet-started rather than neglected, and
 * is excluded from the aggregate. Without this, a single long-abandoned subdeck would make its
 * whole parent branch look like the most overdue thing to study, which isn't the point of an
 * oldest-first sort.
 */
private const val IGNORE_STALE_SUBDECK_DAYS = 100L
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Aggregates [lastStudiedByDeck] entries across [this] node and all of its descendants.
 *
 * - [DeckSortOrder.MOST_RECENT] (and [DeckSortOrder.NAME]): the most recent review, mirroring how
 *   card counts aggregate subdecks.
 * - [DeckSortOrder.LEAST_RECENT]: the oldest review, but only among entries studied within
 *   [IGNORE_STALE_SUBDECK_DAYS] days — older ones are excluded rather than dragging the parent's
 *   date out further.
 */
private fun DeckNode.aggregatedLastStudiedMillis(
    lastStudiedByDeck: Map<DeckId, Long>,
    order: DeckSortOrder,
    dayStartMillis: Long,
): Long? {
    val values = mapNotNull { lastStudiedByDeck[it.did] }
    if (order != DeckSortOrder.LEAST_RECENT) return values.maxOrNull()
    val ignoreThresholdMs = IGNORE_STALE_SUBDECK_DAYS * MILLIS_PER_DAY
    return values.filter { dayStartMillis - it < ignoreThresholdMs }.minOrNull()
}

/**
 * An immutable variant of a [DeckNode]. Instantiated right before
 * we want to display it. The list being submitted to the [ListViewAdapter]
 * is a list of [DisplayDeckNode]s. This class only contains the information
 * needed to display it on the screen, hence no data of a node's children and parent.
 */
@ConsistentCopyVisibility
data class DisplayDeckNode private constructor(
    val did: DeckId,
    val fullDeckName: String,
    val lastDeckNameComponent: String,
    val collapsed: Boolean,
    val canCollapse: Boolean,
    val depth: Int,
    val filtered: Boolean,
    val newCount: Int,
    val lrnCount: Int,
    val revCount: Int,
    val isSelected: Boolean,
    /**
     * Most recent review across this deck and all of its subdecks, as epoch milliseconds, or
     * `null` if neither this deck nor any subdeck has ever been studied.
     */
    val lastStudiedMillis: Long?,
) {
    // DeckNode is mutable, so use a lateinit var so '==' doesn't include it in the comparison
    lateinit var deckNode: DeckNode

    /**
     * Whether this deck has anything left to study today. The counts already include subdecks and
     * respect the deck's daily limits, so this is false once the limit has been used up.
     */
    val hasCardsReadyToStudy: Boolean get() = newCount > 0 || lrnCount > 0 || revCount > 0

    fun withUpdatedDeckId(deckId: DeckId): DisplayDeckNode =
        this.copy(isSelected = this.did == deckId).also { updated ->
            updated.deckNode = this.deckNode
        }

    companion object {
        fun from(
            node: DeckNode,
            matchesSearchOrChild: Boolean,
            selectedDeckId: DeckId,
            lastStudiedByDeck: Map<DeckId, Long>,
            order: DeckSortOrder,
            dayStartMillis: Long,
        ): DisplayDeckNode =
            DisplayDeckNode(
                did = node.did,
                fullDeckName = node.fullDeckName,
                lastDeckNameComponent = node.lastDeckNameComponent,
                collapsed = node.collapsed,
                canCollapse = node.children.any() && matchesSearchOrChild,
                depth = node.depth,
                filtered = node.filtered,
                newCount = node.newCount,
                lrnCount = node.lrnCount,
                revCount = node.revCount,
                isSelected = node.did == selectedDeckId,
                lastStudiedMillis = node.aggregatedLastStudiedMillis(lastStudiedByDeck, order, dayStartMillis),
            ).apply {
                this.deckNode = node
            }
    }
}

/** Convert the tree into a flat list of [DisplayDeckNode]s, where matching decks and the children/parents
 * are included. Decks inside collapsed decks are not considered. */
fun DeckNode.filterAndFlattenDisplay(
    filter: DeckFilters,
    selectedDeckId: DeckId,
    lastStudiedByDeck: Map<DeckId, Long> = emptyMap(),
    order: DeckSortOrder = DeckSortOrder.NAME,
    dayStartMillis: Long = 0L,
): List<DisplayDeckNode> {
    val list = mutableListOf<DisplayDeckNode>()
    filterAndFlattenDisplayInner(filter, list, parentMatched = false, selectedDeckId, lastStudiedByDeck, order, dayStartMillis)
    return list
}

private fun DeckNode.filterAndFlattenDisplayInner(
    filter: DeckFilters,
    list: MutableList<DisplayDeckNode>,
    parentMatched: Boolean,
    selectedDeckId: DeckId,
    lastStudiedByDeck: Map<DeckId, Long>,
    order: DeckSortOrder,
    dayStartMillis: Long,
) {
    if (!isSyntheticDeck && (filter.accept(fullDeckName) || parentMatched)) {
        this.addVisibleToList(list, matchesSearchOrChild = true, selectedDeckId, lastStudiedByDeck, order, dayStartMillis)
        return
    }

    // When searching, ignore collapsed state and always search children
    val searching = filter.isActive()
    if (collapsed && !searching) {
        return
    }

    if (!isSyntheticDeck) {
        list.append(
            DisplayDeckNode.from(
                this,
                matchesSearchOrChild = false,
                selectedDeckId = selectedDeckId,
                lastStudiedByDeck = lastStudiedByDeck,
                order = order,
                dayStartMillis = dayStartMillis,
            ),
        )
    }
    val startingLen = list.size
    for (child in children) {
        child.filterAndFlattenDisplayInner(filter, list, parentMatched = false, selectedDeckId, lastStudiedByDeck, order, dayStartMillis)
    }
    if (!isSyntheticDeck && startingLen == list.size) {
        // we don't include ourselves if no children matched
        list.removeAt(list.lastIndex)
    }
}

private fun DeckNode.addVisibleToList(
    list: MutableList<DisplayDeckNode>,
    matchesSearchOrChild: Boolean,
    selectedDeckId: DeckId,
    lastStudiedByDeck: Map<DeckId, Long>,
    order: DeckSortOrder,
    dayStartMillis: Long,
) {
    list.append(DisplayDeckNode.from(this, matchesSearchOrChild, selectedDeckId, lastStudiedByDeck, order, dayStartMillis))
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list, matchesSearchOrChild, selectedDeckId, lastStudiedByDeck, order, dayStartMillis)
        }
    }
}

@VisibleForTesting
fun DeckNode.addVisibleToList(list: MutableList<DeckNode>) {
    list.append(this)
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list)
        }
    }
}
