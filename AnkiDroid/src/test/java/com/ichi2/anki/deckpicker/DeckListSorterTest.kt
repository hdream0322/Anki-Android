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
    // 30 days = MOST_RECENT stale threshold; 100 days = LEAST_RECENT ignore threshold.
    private val dayStartMillis = 1_000_000_000_000L

    private val freshRecent = dayStartMillis - 1 * 86_400_000L // 1 day ago
    private val freshOld = dayStartMillis - 10 * 86_400_000L // 10 days ago
    private val stale = dayStartMillis - 40 * 86_400_000L // 40 days ago (>30, <100)
    private val veryStale = dayStartMillis - 150 * 86_400_000L // 150 days ago (>100)
    private val never: Long? = null

    private var nextId = 1L

    /**
     * @param dueCount cards waiting today, as the backend reports them (already including subdecks).
     *   Defaults to 1 so a deck has something to study unless a test says otherwise.
     */
    private fun makeNode(
        name: String,
        lastStudied: Long?,
        children: List<Pair<DeckNode, Long?>> = emptyList(),
        dueCount: Int = 1,
    ): Pair<DeckNode, Long?> {
        val id = nextId++
        val treeNode =
            deckTreeNode {
                this.name = name
                this.deckId = id
                this.level = 1
                this.collapsed = false
                children.forEach { this.children.add(it.first.node) }
                this.reviewCount = dueCount
                this.newCount = 0
                this.learnCount = 0
                this.filtered = false
            }
        return DeckNode(treeNode, name) to lastStudied
    }

    private fun flatList(
        vararg pairs: Pair<DeckNode, Long?>,
        order: DeckSortOrder,
    ): List<DisplayDeckNode> {
        val lastStudiedByDeck = pairs.mapNotNull { (node, ms) -> ms?.let { node.did to ms } }.toMap()
        val rootNode =
            deckTreeNode {
                this.name = ""
                this.deckId = 0
                this.level = 0
                pairs.forEach { this.children.add(it.first.node) }
            }
        val root = DeckNode(rootNode, "")
        return root.filterAndFlattenDisplay(DeckFilters.create(""), selectedDeckId = -1, lastStudiedByDeck, order, dayStartMillis)
    }

    @Test
    fun `NAME order leaves list unchanged`() {
        val (a) = makeNode("A", freshOld)
        val (b) = makeNode("B", freshRecent)
        val (c) = makeNode("C", stale)
        val list = flatList(a to freshOld, b to freshRecent, c to stale, order = DeckSortOrder.NAME)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.NAME, dayStartMillis)
        assertEquals(listOf("A", "B", "C"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT puts oldest-studied first`() {
        val (recent) = makeNode("Recent", freshRecent)
        val (old) = makeNode("Old", freshOld)
        val list = flatList(recent to freshRecent, old to freshOld, order = DeckSortOrder.LEAST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("Old", "Recent"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `MOST_RECENT puts newest-studied first`() {
        val (old) = makeNode("Old", freshOld)
        val (recent) = makeNode("Recent", freshRecent)
        val list = flatList(old to freshOld, recent to freshRecent, order = DeckSortOrder.MOST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.MOST_RECENT, dayStartMillis)
        assertEquals(listOf("Recent", "Old"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `MOST_RECENT pins decks idle 30+ days to bottom`() {
        val (old) = makeNode("Old", freshOld)
        val (staleNode) = makeNode("Stale", stale)
        val (recent) = makeNode("Recent", freshRecent)
        val list = flatList(staleNode to stale, old to freshOld, recent to freshRecent, order = DeckSortOrder.MOST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.MOST_RECENT, dayStartMillis)
        assertEquals(listOf("Recent", "Old", "Stale"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT does not pin a 40-day-old deck to bottom`() {
        // 40 days is >30 (the old MOST_RECENT threshold) but <100 (the LEAST_RECENT ignore
        // threshold), so it should still sort normally as the oldest-studied deck.
        val (recent) = makeNode("Recent", freshRecent)
        val (staleNode) = makeNode("Stale", stale)
        val list = flatList(recent to freshRecent, staleNode to stale, order = DeckSortOrder.LEAST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("Stale", "Recent"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT pins decks idle 100+ days to bottom as not-yet-started`() {
        val (old) = makeNode("Old", freshOld)
        val (veryStaleNode) = makeNode("VeryStale", veryStale)
        val (recent) = makeNode("Recent", freshRecent)
        val list =
            flatList(veryStaleNode to veryStale, old to freshOld, recent to freshRecent, order = DeckSortOrder.LEAST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("Old", "Recent", "VeryStale"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `never-studied decks treated as stale in both orders`() {
        val (a) = makeNode("A", freshOld)
        val (neverNode) = makeNode("Never", never)
        val leastRecentList = flatList(neverNode to never, a to freshOld, order = DeckSortOrder.LEAST_RECENT)
        assertEquals(
            listOf("A", "Never"),
            leastRecentList.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis).map { it.lastDeckNameComponent },
        )
        val mostRecentList = flatList(neverNode to never, a to freshOld, order = DeckSortOrder.MOST_RECENT)
        assertEquals(
            listOf("A", "Never"),
            mostRecentList.sortedByStudyOrder(DeckSortOrder.MOST_RECENT, dayStartMillis).map { it.lastDeckNameComponent },
        )
    }

    @Test
    fun `LEAST_RECENT puts decks with nothing due today below decks with cards waiting`() {
        // The point of oldest-first is to surface decks whose backlog has piled up. A deck last
        // studied 10 days ago but with nothing due today needs no work, so it must not outrank a
        // deck studied yesterday that still has cards waiting.
        val (idle) = makeNode("Idle", freshOld, dueCount = 0)
        val (due) = makeNode("Due", freshRecent)
        val list = flatList(idle to freshOld, due to freshRecent, order = DeckSortOrder.LEAST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("Due", "Idle"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT keeps oldest-first within the nothing-due group`() {
        val (idleOld) = makeNode("IdleOld", freshOld, dueCount = 0)
        val (idleRecent) = makeNode("IdleRecent", freshRecent, dueCount = 0)
        val (due) = makeNode("Due", freshRecent)
        val list =
            flatList(idleRecent to freshRecent, due to freshRecent, idleOld to freshOld, order = DeckSortOrder.LEAST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("Due", "IdleOld", "IdleRecent"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT still pins never-studied decks below the nothing-due group`() {
        val (idle) = makeNode("Idle", freshOld, dueCount = 0)
        val (neverNode) = makeNode("Never", never)
        val (due) = makeNode("Due", freshRecent)
        val list = flatList(neverNode to never, idle to freshOld, due to freshRecent, order = DeckSortOrder.LEAST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("Due", "Idle", "Never"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `MOST_RECENT ignores due counts`() {
        val (idle) = makeNode("Idle", freshRecent, dueCount = 0)
        val (due) = makeNode("Due", freshOld)
        val list = flatList(due to freshOld, idle to freshRecent, order = DeckSortOrder.MOST_RECENT)
        val sorted = list.sortedByStudyOrder(DeckSortOrder.MOST_RECENT, dayStartMillis)
        assertEquals(listOf("Idle", "Due"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT parent with a due subdeck stays above an idle deck`() {
        // Lang itself has no cards of its own, but the backend's count includes its subdecks, so a
        // parent whose child still has cards waiting must stay in the "has work" group.
        val (english) = makeNode("English", freshRecent, dueCount = 2)
        val (lang) = makeNode("Lang", null, listOf(english to freshRecent), dueCount = 2)
        val (idle) = makeNode("Idle", freshOld, dueCount = 0)

        val lastStudiedByDeck = mapOf(english.did to freshRecent, idle.did to freshOld)
        val rootNode =
            deckTreeNode {
                this.name = ""
                this.deckId = 0
                this.level = 0
                this.children.add(lang.node)
                this.children.add(idle.node)
            }
        val root = DeckNode(rootNode, "")
        val list =
            root.filterAndFlattenDisplay(
                DeckFilters.create(""),
                selectedDeckId = -1,
                lastStudiedByDeck,
                DeckSortOrder.LEAST_RECENT,
                dayStartMillis,
            )
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals(listOf("Lang", "English", "Idle"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `MOST_RECENT parent shows most-recently-studied subdeck`() {
        // Lang (parent, never studied directly) has child English (1d ago).
        // Math was studied 10d ago.
        // MOST_RECENT: Lang/English (1d) before Math (10d).
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
        val list =
            root.filterAndFlattenDisplay(
                DeckFilters.create(""),
                selectedDeckId = -1,
                lastStudiedByDeck,
                DeckSortOrder.MOST_RECENT,
                dayStartMillis,
            )
        val sorted = list.sortedByStudyOrder(DeckSortOrder.MOST_RECENT, dayStartMillis)
        assertEquals(listOf("Lang", "English", "Math"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT parent shows oldest-studied subdeck, ignoring subdecks over 100 days idle`() {
        // Lang has two children: English (1d ago, fresh) and French (150d ago, ignored as
        // not-yet-started). Lang's LEAST_RECENT date should be English's (1d), not French's.
        val (english) = makeNode("English", freshRecent)
        val (french) = makeNode("French", veryStale)
        val (lang) = makeNode("Lang", null, listOf(english to freshRecent, french to veryStale))
        val (math) = makeNode("Math", freshOld)

        val lastStudiedByDeck = mapOf(english.did to freshRecent, french.did to veryStale, math.did to freshOld)
        val rootNode =
            deckTreeNode {
                this.name = ""
                this.deckId = 0
                this.level = 0
                this.children.add(lang.node)
                this.children.add(math.node)
            }
        val root = DeckNode(rootNode, "")
        val list =
            root.filterAndFlattenDisplay(
                DeckFilters.create(""),
                selectedDeckId = -1,
                lastStudiedByDeck,
                DeckSortOrder.LEAST_RECENT,
                dayStartMillis,
            )
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        // Lang picks up English's 1d (not French's 150d), so it sorts alongside its freshest child,
        // ahead of Math (10d ago) which is older than either.
        assertEquals(listOf("Math", "Lang", "English", "French"), sorted.map { it.lastDeckNameComponent })
    }

    @Test
    fun `LEAST_RECENT parent with all subdecks over 100 days idle is pinned to bottom`() {
        // Both of Lang's children are past the ignore threshold, so Lang has no eligible date
        // and is pinned to the bottom, same as a never-studied deck.
        val (english) = makeNode("English", veryStale)
        val (french) = makeNode("French", veryStale)
        val (lang) = makeNode("Lang", null, listOf(english to veryStale, french to veryStale))
        val (math) = makeNode("Math", freshOld)

        val lastStudiedByDeck = mapOf(english.did to veryStale, french.did to veryStale, math.did to freshOld)
        val rootNode =
            deckTreeNode {
                this.name = ""
                this.deckId = 0
                this.level = 0
                this.children.add(lang.node)
                this.children.add(math.node)
            }
        val root = DeckNode(rootNode, "")
        val list =
            root.filterAndFlattenDisplay(
                DeckFilters.create(""),
                selectedDeckId = -1,
                lastStudiedByDeck,
                DeckSortOrder.LEAST_RECENT,
                dayStartMillis,
            )
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        assertEquals("Math", sorted.first().lastDeckNameComponent)
        assertEquals(setOf("Lang", "English", "French"), sorted.drop(1).map { it.lastDeckNameComponent }.toSet())
    }

    @Test
    fun `flat sort ignores parent-child nesting`() {
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
        val list =
            root.filterAndFlattenDisplay(
                DeckFilters.create(""),
                selectedDeckId = -1,
                lastStudiedByDeck,
                DeckSortOrder.LEAST_RECENT,
                dayStartMillis,
            )
        val sorted = list.sortedByStudyOrder(DeckSortOrder.LEAST_RECENT, dayStartMillis)
        // All decks sorted together: Math(10d) before Lang(1d) and English(1d)
        assertEquals(listOf("Math", "Lang", "English"), sorted.map { it.lastDeckNameComponent })
    }
}
