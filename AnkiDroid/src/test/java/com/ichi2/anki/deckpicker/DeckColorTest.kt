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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.decks.deckTreeNode
import com.ichi2.anki.libanki.sched.DeckNode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class DeckColorTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("deck_color_test", Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }

    @Test
    fun `no colors by default`() {
        assertEquals(emptyMap<Long, DeckColor>(), prefs.deckColors())
    }

    @Test
    fun `set color is read back per deck`() {
        prefs.setDeckColor(1L, DeckColor.MINT)
        prefs.setDeckColor(2L, DeckColor.PINK)

        assertEquals(mapOf(1L to DeckColor.MINT, 2L to DeckColor.PINK), prefs.deckColors())
    }

    @Test
    fun `setting null clears the color`() {
        prefs.setDeckColor(1L, DeckColor.SKY)
        prefs.setDeckColor(1L, null)

        assertEquals(emptyMap<Long, DeckColor>(), prefs.deckColors())
    }

    @Test
    fun `unknown stored value and unrelated keys are ignored`() {
        prefs.edit {
            putString("deurim_deck_color_5", "NOT_A_COLOR")
            putString("some_other_pref", "MINT")
        }

        assertEquals(emptyMap<Long, DeckColor>(), prefs.deckColors())
    }

    @Test
    fun `flattened list carries each deck's own color only`() {
        val child =
            deckTreeNode {
                name = "Child"
                deckId = 11
                level = 2
            }
        val parent =
            deckTreeNode {
                name = "Parent"
                deckId = 10
                level = 1
                children.add(child)
            }
        val root =
            DeckNode(
                deckTreeNode {
                    name = ""
                    deckId = 0
                    level = 0
                    children.add(parent)
                },
                "",
            )

        val list =
            root.filterAndFlattenDisplay(
                DeckFilters.create(""),
                selectedDeckId = -1,
                colorByDeck = mapOf(10L to DeckColor.LEMON),
            )

        assertEquals(DeckColor.LEMON, list.single { it.did == 10L }.color)
        assertNull(list.single { it.did == 11L }.color)
    }
}
