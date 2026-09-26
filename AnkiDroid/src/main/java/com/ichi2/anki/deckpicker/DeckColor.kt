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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckId
import com.ichi2.themes.Themes
import com.ichi2.utils.dp
import com.ichi2.utils.negativeButton
import com.ichi2.utils.show
import com.ichi2.utils.title

private const val PREF_DECK_COLOR_PREFIX = "deurim_deck_color_"

/**
 * A pastel highlight the user can give a deck row in the deck list, to mark it out.
 *
 * Stored only on this device (SharedPreferences), never in the collection, so it doesn't sync.
 *
 * @param lightColor row background for day themes
 * @param darkColor row background for night themes: a deep tone of the same hue, so light text
 *   stays readable
 */
enum class DeckColor(
    @StringRes val labelRes: Int,
    @ColorInt val lightColor: Int,
    @ColorInt val darkColor: Int,
) {
    PINK(R.string.deck_color_pink, 0xFFFFD9E3.toInt(), 0xFF5A3441.toInt()),
    PEACH(R.string.deck_color_peach, 0xFFFFE3CC.toInt(), 0xFF5C4130.toInt()),
    LEMON(R.string.deck_color_lemon, 0xFFFFF4C2.toInt(), 0xFF534C28.toInt()),
    MINT(R.string.deck_color_mint, 0xFFD5F2E3.toInt(), 0xFF2D4C3C.toInt()),
    SKY(R.string.deck_color_sky, 0xFFD6E8FF.toInt(), 0xFF2E4260.toInt()),
    ;

    @ColorInt
    fun color(isNight: Boolean): Int = if (isNight) darkColor else lightColor
}

/** Every deck with a [DeckColor], keyed by deck id. Unknown or malformed entries are skipped. */
fun SharedPreferences.deckColors(): Map<DeckId, DeckColor> =
    all.entries
        .mapNotNull { (key, value) ->
            val did = key.removePrefix(PREF_DECK_COLOR_PREFIX).takeIf { it != key }?.toLongOrNull()
            val color = DeckColor.entries.find { it.name == value }
            if (did == null || color == null) null else did to color
        }.toMap()

/**
 * Sets the color of [did], or clears it when [color] is `null`.
 *
 * Entries of deleted decks are left in place: deck ids are creation timestamps and never reused,
 * and keeping the entry lets an undone deletion get its color back.
 */
fun SharedPreferences.setDeckColor(
    did: DeckId,
    color: DeckColor?,
) {
    edit {
        if (color == null) remove(PREF_DECK_COLOR_PREFIX + did) else putString(PREF_DECK_COLOR_PREFIX + did, color.name)
    }
}

/**
 * Shows a single-choice list of "No color" followed by each [DeckColor] with a swatch, with
 * [current] checked. [onPicked] receives the choice (`null` for "No color").
 */
fun Context.showDeckColorPicker(
    current: DeckColor?,
    onPicked: (DeckColor?) -> Unit,
) {
    val choices = listOf(null) + DeckColor.entries
    val labels =
        choices
            .map { color ->
                if (color == null) {
                    getString(R.string.deck_color_none)
                } else {
                    // "\u00A0\u00A0" is a placeholder the swatch is drawn over, followed by a gap
                    SpannableString("\u00A0\u00A0  " + getString(color.labelRes)).apply {
                        setSpan(ImageSpan(swatch(color.color(Themes.isNightTheme))), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }.toTypedArray<CharSequence>()
    AlertDialog.Builder(this).show {
        title(R.string.deck_color)
        setSingleChoiceItems(labels, choices.indexOf(current)) { dialog, index ->
            onPicked(choices[index])
            dialog.dismiss()
        }
        negativeButton(R.string.dialog_cancel)
    }
}

/** A filled circle with a faint outline, so pale day-theme swatches stay visible on white. */
private fun Context.swatch(
    @ColorInt color: Int,
): GradientDrawable {
    val size = 20.dp.toPx(this)
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(1.dp.toPx(this@swatch), Color.argb(0x40, 0x80, 0x80, 0x80))
        setBounds(0, 0, size, size)
    }
}
