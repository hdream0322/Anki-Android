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

import anki.decks.deckTreeNode
import com.ichi2.anki.libanki.sched.DeckNode
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckListSorterTest {
    // A fixed "now" for tests: epoch ms at which the current Anki day begins.
    // 30 days = stale threshold.
    private val dayStartMillis = 1_000_000_000_000L

    private val freshRecent = dayStartMillis - 1 * 86_400_000L // 1 day ago
    private val freshOld = dayStartMillis - 10 * 86_400_000L // 10 days ago
    private val stale = dayStartMillis - 40 * 86_400_000L // 40 days ago (>30)
    private val never: Long? = null

    private var nextId = 1L

    private fun makeNode(
        name: String,
        lastStudied: Long?,
        children: List<Pair<DeckNode, Long?>> = emptyList(),
    ): Pair<DeckNode, Long?> {
        val id = nextId++
        val treeNode =
            deckTreeNode {
                this.name = name
                this.deckId = id
                this.level = 1
                this.collapsed = false
                children.forEach { this.children.add(it.first.node) }
                this.reviewCount = 0
                this.newCount = 0
                this.learnCount = 0
                this.filtered = false
            }
        return DeckNode(treeNode, name) to lastStudied
    }

    private fun flatList(vararg pairs: Pair<DeckNode, Long?>): List<DisplayDeckNode> {
        val lastStudiedByDeck = pairs.mapNotNull { (node, ms) -> ms?.let { node.did to ms } }.toMap()
        val rootNode =
            deckTreeNode {
                this.name = ""
                this.deckId = 0
                this.level = 0
                pairs.forEach { this.children.add(it.first.node) }
            }
        val root = DeckNode(rootNode, "")
        return root.filterAndFlattenDisplay(DeckFilters.create(""), selectedDeckId = -1, lastStudiedByDeck)
    }

    @Test
    fun `NAME order leaves list unchanged`() {
        val (a) = makeNode("A", freshOld)
        val (b) = makeNode("B", freshRecent)
        val (c) = makeNode("C", stale)
        val list = flatList(a to freshOld, b to freshRecent, c to stale)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.NAME, dayStartMillis)
        assertEquals(listOf("A", "B", "C"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT puts oldest-studied first`() {
        val (recent) = makeNode("Recent", freshRecent)
        val (old) = makeNode("Old", freshOld)
        val list = flatList(recent to freshRecent, old to freshOld)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("Old", "Recent"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `MOST_RECENT puts newest-studied first`() {
        val (old) = makeNode("Old", freshOld)
        val (recent) = makeNode("Recent", freshRecent)
        val list = flatList(old to freshOld, recent to freshRecent)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.MOST_RECENT, dayStartMillis)
        assertEquals(listOf("Recent", "Old"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `stale decks are pinned to bottom`() {
        val (old) = makeNode("Old", freshOld)
        val (staleNode) = makeNode("Stale", stale)
        val (recent) = makeNode("Recent", freshRecent)
        val list = flatList(staleNode to stale, old to freshOld, recent to freshRecent)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        // fresh decks first (old before recent in LEAST_RECENT), stale at bottom
        assertEquals(listOf("Old", "Recent", "Stale"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `never-studied decks treated as stale`() {
        val (a) = makeNode("A", freshOld)
        val (neverNode) = makeNode("Never", never)
        val list = flatList(neverNode to never, a to freshOld)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("A", "Never"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `parent order reflects max child study time`() {
        // Parent "Lang" has a child studied 1d ago → parent's lastStudiedMillis = 1d ago.
        // Parent "Math" was studied 10d ago, no children.
        // LEAST_RECENT: Math (10d) before Lang (1d).
        val (child) = makeNode("English", freshRecent)
        val (lang) = makeNode("Lang", null, listOf(child to freshRecent))
        val (math) = makeNode("Math", freshOld)

        val lastStudiedByDeck = mapOf(child.did to freshRecent, math.did to freshOld)
        val rootNode =
            deckTreeNode {
                this.name = ""
                this.deckId = 0
                this.level = 0
                this.children.add(lang.node)
                this.children.add(math.node)
            }
        val root = DeckNode(rootNode, "")
        val list = root.filterAndFlattenDisplay(DeckFilters.create(""), selectedDeckId = -1, lastStudiedByDeck)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        // top-level order: Math(10d) before Lang(1d via child)
        val topLevel = sorted.filter { it.depth == 0 }.map { it.lastDeckNameComponent }
        assertEquals(listOf("Math", "Lang"), topLevel)
    }
}
