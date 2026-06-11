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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.os.BundleCompat
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.unregisterReceiverSilently
import com.ichi2.anki.databinding.ActivityHeatmapWidgetConfigBinding
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.withProgress
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import com.ichi2.widget.heatmap.HeatmapWidget.Companion.EXTRA_SELECTED_DECK_ID
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Config screen for [HeatmapWidget]. User picks one deck whose review heatmap
 * will be drawn. Modelled after CardAnalysisWidgetConfig.
 */
class HeatmapWidgetConfig : AnkiActivity(R.layout.activity_heatmap_widget_config) {
    private val binding by viewBinding(ActivityHeatmapWidgetConfigBinding::bind)

    private var appWidgetId = INVALID_APPWIDGET_ID
    private var deck: SelectableDeck.Deck? = null
    private lateinit var preferences: HeatmapWidgetPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) return
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) return

        preferences = HeatmapWidgetPreferences(this)
        appWidgetId = intent.getAppWidgetId()
        if (appWidgetId == INVALID_APPWIDGET_ID) {
            Timber.v("Invalid App Widget ID")
            finish()
            return
        }
        if (savedInstanceState != null) {
            deck =
                BundleCompat.getParcelable(
                    savedInstanceState,
                    KEY_DECK,
                    SelectableDeck.Deck::class.java,
                )
            binding.deckName.text = deck?.name
        } else {
            loadContent()
        }
        binding.changeBtn.setOnClickListener { showDeckSelectionDialog() }
        binding.doneBtn.setOnClickListener { close() }
        registerReceiver(
            widgetRemovedReceiver,
            IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED),
        )
        registerDeckSelectedHandler(action = ::onDeckSelected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_DECK, deck)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSilently(widgetRemovedReceiver)
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null || deck !is SelectableDeck.Deck?) {
            showThemedToast(this, R.string.something_wrong, false)
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val shouldClose = this.deck == null
        this.deck = deck
        binding.deckName.text = deck.name
        preferences.saveSelectedDeck(appWidgetId, deck.deckId)
        updateWidget()
        if (shouldClose) close()
    }

    private fun loadContent() {
        launchCatchingTask {
            withProgress {
                if (isCollectionEmpty()) {
                    showThemedToast(this@HeatmapWidgetConfig, R.string.no_cards_placeholder_title, false)
                    finish()
                    return@withProgress
                }
                val selectedDeckId = preferences.getSelectedDeckIdFromPreferences(appWidgetId)
                if (selectedDeckId == null) {
                    showDeckSelectionDialog()
                } else {
                    deck = SelectableDeck.Deck.fromId(selectedDeckId)
                    binding.deckName.text = deck?.name ?: TR.sentenceCase.selectDeck
                }
            }
        }
    }

    private fun showDeckSelectionDialog() {
        startDeckSelection(
            title = getString(R.string.select_deck_title),
            allowAll = false,
            skipEmptyDefault = true,
        )
    }

    private fun updateWidget() {
        val updateIntent =
            Intent(this, HeatmapWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId.id))
                putExtra(EXTRA_SELECTED_DECK_ID, deck?.deckId)
            }
        sendBroadcast(updateIntent)
        HeatmapWidget.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
    }

    private fun close() {
        val intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)
        setResult(RESULT_OK, intent)
        finish()
    }

    private val widgetRemovedReceiver =
        object : AnkiBroadcastReceiver() {
            override fun onReceiveBroadcast(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != AppWidgetManager.ACTION_APPWIDGET_DELETED) return
                val id = intent.getAppWidgetId()
                if (id == INVALID_APPWIDGET_ID) return
                preferences.deleteDeckData(id)
            }
        }

    companion object {
        private const val KEY_DECK = "key_deck"
    }
}
