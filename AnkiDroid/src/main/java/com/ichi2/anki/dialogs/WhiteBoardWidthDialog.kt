/*
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import java.util.function.Consumer

class WhiteBoardWidthDialog(
    private val context: Context,
    private var wbStrokeWidth: Int,
) {
    private var strokeWidthText: FixedTextView? = null
    private var seekBar: SeekBar? = null
    var onStrokeWidthChanged: Consumer<Int>? = null
    private val seekBarChangeListener: OnSeekBarChangeListener =
        object : OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                value: Int,
                b: Boolean,
            ) {
                wbStrokeWidth = value
                strokeWidthText!!.text = "" + value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // intentionally blank
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // intentionally blank
            }
        }

    fun showStrokeWidthDialog() {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPaddingRelative(6, 6, 6, 6)

        // Row with [-] [value] [+] so the user can nudge by 1 without
        // fighting the seek bar for fine-grained adjustments.
        val valueRow = LinearLayout(context)
        valueRow.orientation = LinearLayout.HORIZONTAL
        valueRow.gravity = Gravity.CENTER

        val minusButton =
            Button(context).apply {
                text = "−"
                setOnClickListener { adjustStrokeWidth(-1) }
            }
        val plusButton =
            Button(context).apply {
                text = "+"
                setOnClickListener { adjustStrokeWidth(1) }
            }

        strokeWidthText =
            FixedTextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 30f
                text = "" + wbStrokeWidth
            }

        val buttonParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val textParams =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
                marginEnd = 12
            }
        valueRow.addView(minusButton, buttonParams)
        valueRow.addView(strokeWidthText, textParams)
        valueRow.addView(plusButton, buttonParams)

        layout.addView(
            valueRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )

        seekBar =
            SeekBar(context).apply {
                progress = wbStrokeWidth
                setOnSeekBarChangeListener(seekBarChangeListener)
            }
        layout.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        AlertDialog.Builder(context).show {
            title(R.string.whiteboard_stroke_width)
            positiveButton(R.string.save) {
                onStrokeWidthChanged?.accept(wbStrokeWidth)
            }
            negativeButton(R.string.dialog_cancel)
            setView(layout)
        }
    }

    private fun adjustStrokeWidth(delta: Int) {
        val bar = seekBar ?: return
        val newValue = (wbStrokeWidth + delta).coerceIn(0, bar.max)
        if (newValue == wbStrokeWidth) return
        // Setting the SeekBar progress triggers the listener, which updates
        // wbStrokeWidth and the text view.
        bar.progress = newValue
    }

    fun onStrokeWidthChanged(c: Consumer<Int>?) {
        onStrokeWidthChanged = c
    }
}
