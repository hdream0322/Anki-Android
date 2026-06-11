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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.heatmap.fetchReviewHeatmapData
import com.ichi2.anki.libanki.Decks.Companion.NOT_FOUND_DECK_ID
import com.ichi2.widget.ACTION_UPDATE_WIDGET
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import com.ichi2.widget.AppWidgetIds
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.cancelRecurringAlarm
import com.ichi2.widget.getAppWidgetIdsEx
import com.ichi2.widget.setRecurringAlarm
import com.ichi2.widget.updateAppWidget
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Home-screen widget showing the review heatmap (contribution-graph style) for a
 * single deck chosen at configuration time. Reuses [com.ichi2.anki.heatmap.ReviewHeatmapView]
 * via [HeatmapBitmapRenderer] since RemoteViews can't host custom views directly.
 */
class HeatmapWidget : AnalyticsWidgetProvider() {
    companion object {
        const val EXTRA_SELECTED_DECK_ID = "heatmap_widget_selected_deck_id"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
        ) {
            val deckId = HeatmapWidgetPreferences(context).getSelectedDeckIdFromPreferences(appWidgetId) ?: NOT_FOUND_DECK_ID
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_heatmap)

            if (deckId == NOT_FOUND_DECK_ID) {
                showMissingDeck(context, appWidgetManager, appWidgetId, remoteViews)
                return
            }

            // Pull the heatmap data + render to a bitmap sized to the current widget width.
            applicationScope.launch {
                try {
                    val (data, deckName) =
                        withCol {
                            val name = decks.get(deckId)?.name
                            val payload = fetchReviewHeatmapData(deckId)
                            payload to name
                        }
                    if (deckName == null) {
                        HeatmapWidgetPreferences(context).saveSelectedDeck(appWidgetId, NOT_FOUND_DECK_ID)
                        showMissingDeck(context, appWidgetManager, appWidgetId, remoteViews)
                        return@launch
                    }

                    val widthPx = currentWidgetWidthPx(context, appWidgetManager, appWidgetId)
                    val bitmap = HeatmapBitmapRenderer.render(context, data, widthPx)

                    remoteViews.setTextViewText(R.id.heatmap_widget_deck_name, deckName)
                    val streakSummary =
                        context.getString(
                            R.string.heatmap_summary,
                            data.currentStreak,
                            data.longestStreak,
                            data.dailyAverage,
                            data.daysLearnedPercent,
                            data.totalReviews,
                            data.dueByDate.values.sum(),
                        )
                    remoteViews.setTextViewText(R.id.heatmap_widget_summary, streakSummary)
                    remoteViews.setImageViewBitmap(R.id.heatmap_widget_image, bitmap)

                    remoteViews.setViewVisibility(R.id.empty_widget, View.GONE)
                    remoteViews.setViewVisibility(R.id.heatmap_widget_image, View.VISIBLE)
                    remoteViews.setViewVisibility(R.id.heatmap_widget_deck_name, View.VISIBLE)
                    remoteViews.setViewVisibility(R.id.heatmap_widget_summary, View.VISIBLE)

                    val openIntent =
                        Intent(context, DeckPicker::class.java).apply {
                            putExtra(DeckPicker.EXTRA_DECK_ID_TO_SELECT, deckId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    val pi =
                        PendingIntent.getActivity(
                            context,
                            deckId.toInt(),
                            openIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    remoteViews.setOnClickPendingIntent(R.id.heatmap_widget_root, pi)

                    appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
                } catch (e: Exception) {
                    Timber.w(e, "HeatmapWidget update failed")
                    CrashReportService.sendExceptionReport(e, "HeatmapWidget.updateWidget", onlyIfSilent = true)
                }
            }
        }

        private fun currentWidgetWidthPx(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
        ): Int {
            val opts = appWidgetManager.getAppWidgetOptions(appWidgetId.id)
            val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val density = context.resources.displayMetrics.density
            return (minWidthDp * density).toInt().coerceAtLeast(1)
        }

        private fun showMissingDeck(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            remoteViews: RemoteViews,
        ) {
            remoteViews.setTextViewText(R.id.empty_widget, context.getString(R.string.empty_widget_state))
            remoteViews.setViewVisibility(R.id.empty_widget, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.heatmap_widget_image, View.GONE)
            remoteViews.setViewVisibility(R.id.heatmap_widget_deck_name, View.GONE)
            remoteViews.setViewVisibility(R.id.heatmap_widget_summary, View.GONE)

            val configIntent =
                Intent(context, HeatmapWidgetConfig::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            val pi =
                PendingIntent.getActivity(
                    context,
                    appWidgetId.id,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            remoteViews.setOnClickPendingIntent(R.id.empty_widget, pi)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        fun updateHeatmapWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, HeatmapWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIdsEx(provider)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DayRolloverAlarm.scheduleNext(context)
    }

    override fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: AppWidgetIds,
        usageAnalytics: UsageAnalytics,
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
            setRecurringAlarm(context, widgetId, HeatmapWidget::class.java)
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        val prefs = HeatmapWidgetPreferences(context)
        when (intent.action) {
            ACTION_APPWIDGET_UPDATE -> {
                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId != INVALID_APPWIDGET_ID) {
                    updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                }
            }
            ACTION_UPDATE_WIDGET -> {
                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId != INVALID_APPWIDGET_ID) {
                    updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                // 사용자가 위젯 크기를 바꾸면 새 폭으로 다시 렌더한다.
                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId != INVALID_APPWIDGET_ID) {
                    updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId != INVALID_APPWIDGET_ID) {
                    cancelRecurringAlarm(context, appWidgetId, HeatmapWidget::class.java)
                    prefs.deleteDeckData(appWidgetId)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_ENABLED,
            AppWidgetManager.ACTION_APPWIDGET_DISABLED,
            -> Unit
            else -> {
                CrashReportService.sendExceptionReport(
                    Exception("Unexpected action received: ${intent.action}"),
                    "HeatmapWidget - onReceive",
                    onlyIfSilent = true,
                )
            }
        }
    }

    override fun onDeleted(
        context: Context?,
        appWidgetIds: IntArray?,
    ) {
        if (context == null) return
        val prefs = HeatmapWidgetPreferences(context)
        AppWidgetIds.of(appWidgetIds)?.forEach { appWidgetId ->
            cancelRecurringAlarm(context, appWidgetId, HeatmapWidget::class.java)
            prefs.deleteDeckData(appWidgetId)
        }
    }
}
