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
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import com.ichi2.anki.R
import com.ichi2.anki.heatmap.ReviewHeatmapData
import com.ichi2.anki.heatmap.ReviewHeatmapView

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
        // 다크/라이트 모드에 맞춰 테마를 골라 잡아야 색이 자연스럽게 따라간다.
        val nightMode =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val themedContext =
            ContextThemeWrapper(context, if (nightMode) R.style.Theme_Dark else R.style.Theme_Light)

        val view =
            ReviewHeatmapView(themedContext).apply {
                setData(data)
            }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))

        val bitmap =
            Bitmap.createBitmap(
                view.measuredWidth.coerceAtLeast(1),
                view.measuredHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
