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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.createBitmap
import com.ichi2.anki.heatmap.ReviewHeatmapData
import com.ichi2.anki.heatmap.ReviewHeatmapView
import com.ichi2.anki.common.android.R as CommonR

/**
 * Renders a [ReviewHeatmapView] offscreen into a [Bitmap] sized to [widthPx],
 * so it can be displayed inside RemoteViews (which can't host custom views).
 */
object HeatmapBitmapRenderer {
    fun render(
        context: Context,
        data: ReviewHeatmapData,
        widthPx: Int,
    ): Bitmap {
        // Theme.AnkiDroid is a DayNight theme: it follows the uiMode of [context].
        val themedContext = ContextThemeWrapper(context, CommonR.style.Theme_AnkiDroid)

        val view =
            ReviewHeatmapView(themedContext).apply {
                setData(data)
            }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))

        val bitmap =
            createBitmap(
                view.measuredWidth.coerceAtLeast(1),
                view.measuredHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
