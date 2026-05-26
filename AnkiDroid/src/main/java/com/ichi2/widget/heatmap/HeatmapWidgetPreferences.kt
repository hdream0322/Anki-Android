/*
 *  Copyright (c) 2026 Deurim Fork
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
 *  this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.widget.heatmap

import android.content.Context
import androidx.core.content.edit
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks.Companion.NOT_FOUND_DECK_ID
import com.ichi2.widget.AppWidgetId

/**
 * Per-widget-instance deck selection for the heatmap widget.
 * Modelled after [com.ichi2.widget.cardanalysis.CardAnalysisWidgetPreferences].
 */
class HeatmapWidgetPreferences(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("HeatmapWidgetPrefs", Context.MODE_PRIVATE)

    fun getSelectedDeckIdFromPreferences(appWidgetId: AppWidgetId): DeckId? {
        val saved = prefs.getLong(key(appWidgetId), NOT_FOUND_DECK_ID)
        return saved.takeIf { it != NOT_FOUND_DECK_ID }
    }

    fun saveSelectedDeck(
        appWidgetId: AppWidgetId,
        deckId: DeckId?,
    ) {
        prefs.edit { putLong(key(appWidgetId), deckId ?: NOT_FOUND_DECK_ID) }
    }

    fun deleteDeckData(appWidgetId: AppWidgetId) {
        prefs.edit { remove(key(appWidgetId)) }
    }

    private fun key(appWidgetId: AppWidgetId) = "heatmap_widget_selected_deck_$appWidgetId"
}
