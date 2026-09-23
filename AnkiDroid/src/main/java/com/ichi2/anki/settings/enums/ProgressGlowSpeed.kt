// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import com.ichi2.anki.R

/**
 * [R.array.review_progress_bar_glow_speed_values]
 *
 * @param cycleMs length of one sweep plus the pause before the next one
 */
enum class ProgressGlowSpeed(
    override val entryResId: Int,
    val cycleMs: Long,
) : PrefEnum {
    SLOTH(R.string.review_progress_bar_glow_speed_sloth_value, 6000L),
    SLOW(R.string.review_progress_bar_glow_speed_slow_value, 4000L),
    MEDIUM(R.string.review_progress_bar_glow_speed_medium_value, 2600L),
    FAST(R.string.review_progress_bar_glow_speed_fast_value, 1600L),
    ULTRA(R.string.review_progress_bar_glow_speed_ultra_value, 900L),
}
