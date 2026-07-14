// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the "카드와 함께 확대/축소" (whiteboard-follows-card-zoom) sync feature:
 * strokes must keep a constant on-screen pen width regardless of the card's zoom level, and a
 * card's zoom state must not leak into strokes drawn on a later card.
 */
@RunWith(AndroidJUnit4::class)
class WhiteboardContentSyncTest : RobolectricTest() {
    /** Records the [Paint.getStrokeWidth] used for each drawPath/drawPoint call, in the canvas's pre-transform space. */
    private class RecordingCanvas(
        bitmap: Bitmap,
    ) : Canvas(bitmap) {
        val recordedStrokeWidths = mutableListOf<Float>()

        override fun drawPath(
            path: Path,
            paint: Paint,
        ) {
            if (!path.isEmpty) recordedStrokeWidths.add(paint.strokeWidth)
        }

        override fun drawPoint(
            x: Float,
            y: Float,
            paint: Paint,
        ) {
            recordedStrokeWidths.add(paint.strokeWidth)
        }
    }

    private fun createWhiteboard(strokeWidthPx: Int): Whiteboard {
        val activity = startActivityNormallyOpenCollectionWithIntent(Reviewer::class.java, Intent())
        activity.sharedPrefs().edit { putInt("whiteBoardStrokeWidth", strokeWidthPx) }
        val whiteboard = Whiteboard(activity, true, false)
        whiteboard.layout(0, 0, 300, 300)
        return whiteboard
    }

    private fun drawVerticalStroke(
        whiteboard: Whiteboard,
        x: Float,
        yStart: Float,
        yEnd: Float,
    ) {
        val downTime = SystemClock.uptimeMillis()
        whiteboard.handleTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, yStart, 0))
        whiteboard.handleTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, x, yEnd, 0))
        whiteboard.handleTouchEvent(MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_UP, x, yEnd, 0))
    }

    private fun renderAndRecordStrokeWidths(whiteboard: Whiteboard): List<Float> {
        val canvas = RecordingCanvas(Bitmap.createBitmap(whiteboard.width, whiteboard.height, Bitmap.Config.ARGB_8888))
        whiteboard.draw(canvas)
        return canvas.recordedStrokeWidths
    }

    @Test
    fun `stroke drawn while card is zoomed keeps the configured on-screen width`() {
        val nominalWidth = 20f
        val whiteboard = createWhiteboard(nominalWidth.toInt())
        whiteboard.isContentSyncEnabled = true
        // The card is already zoomed in 2x by the time this stroke is drawn.
        whiteboard.setContentTransform(2f, 0f, 0f)

        drawVerticalStroke(whiteboard, x = 150f, yStart = 100f, yEnd = 200f)

        // onDraw draws through canvas.concat(contentMatrix), which scales stroke width by the
        // matrix's scale (2x) at rasterization time. For the *on-screen* width to still equal
        // nominalWidth, the paint width actually passed to drawPath must be pre-compensated
        // to nominalWidth / scale - i.e. half of the configured value here.
        val strokeWidths = renderAndRecordStrokeWidths(whiteboard)
        assertThat("exactly one stroke should have been drawn", strokeWidths.size, equalTo(1))
        assertThat(
            "stroke width passed to the canvas should be halved to counter the 2x zoom, " +
                "so the rendered on-screen width matches the configured $nominalWidth px",
            strokeWidths[0].toDouble(),
            closeTo((nominalWidth / 2f).toDouble(), 0.01),
        )
    }

    @Test
    fun `resetContentTransform clears zoom state so it does not leak into the next card`() {
        val whiteboard = createWhiteboard(6)
        whiteboard.isContentSyncEnabled = true
        whiteboard.setContentTransform(2.5f, 40f, 30f)
        assertThat(whiteboard.contentScale, equalTo(2.5f))

        // Simulates Reviewer.updateForNewCard() moving on to the next card.
        whiteboard.resetContentTransform()

        assertThat(
            "a new card must start from an unscaled content transform",
            whiteboard.contentScale,
            equalTo(1f),
        )
    }
}
