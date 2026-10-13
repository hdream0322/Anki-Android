// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.Context
import android.graphics.Color
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.annotation.StringRes
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiModelInfo
import com.ichi2.anki.ai.AiModelTier
import java.util.Date
import kotlin.math.roundToLong

private const val SUMMARY_TEXT_SCALE = 0.85f

/** Picker row: the model id, then a smaller, dimmed line with its tier and context size. */
fun Context.aiModelRowLabel(model: AiModelInfo): CharSequence {
    val summary =
        listOfNotNull(
            model.tier?.let { getString(it.labelRes) },
            model.inputTokenLimit?.let { getString(R.string.ai_model_summary_context, formatTokenCount(it)) }
                ?: model.createdEpochSeconds?.let { getString(R.string.ai_model_summary_released, formatDate(it)) },
            getString(R.string.ai_model_detail_deprecated).takeIf { model.isDeprecated },
        ).ifEmpty { listOfNotNull(model.displayName) }
            .joinToString(" · ")
    if (summary.isEmpty()) return model.id
    val summaryColor = MaterialColors.getColor(this, android.R.attr.textColorSecondary, Color.GRAY)
    return buildSpannedString {
        append(model.id)
        append('\n')
        inSpans(RelativeSizeSpan(SUMMARY_TEXT_SCALE), ForegroundColorSpan(summaryColor)) { append(summary) }
    }
}

/**
 * Detail dialog body: what the tier means for cost, quality and typical use, then every fact
 * the vendor's list reported. Facts the vendor did not report are left out rather than guessed.
 */
fun Context.aiModelDetails(model: AiModelInfo): CharSequence =
    buildSpannedString {
        fun line(
            @StringRes label: Int,
            value: String,
        ) {
            if (isNotEmpty()) append('\n')
            bold { append(getString(label)) }
            append("  ")
            append(value)
        }

        model.description?.let { append(it).append('\n') }

        val tier = model.tier
        if (tier == null) {
            append(getString(R.string.ai_model_detail_no_tier))
        } else {
            line(R.string.ai_model_detail_tier, getString(tier.labelRes))
            line(R.string.ai_model_detail_cost, getString(tier.costRes))
            line(R.string.ai_model_detail_quality, getString(tier.qualityRes))
            line(R.string.ai_model_detail_use_case, getString(tier.useCaseRes))
        }
        append('\n')

        line(R.string.ai_model_detail_id, model.id)
        model.inputTokenLimit?.let {
            line(R.string.ai_model_detail_context, getString(R.string.ai_model_detail_context_value, formatTokenCount(it)))
        }
        model.outputTokenLimit?.let {
            line(R.string.ai_model_detail_output, getString(R.string.ai_model_detail_output_value, formatTokenCount(it)))
        }
        inputKinds(model)?.let { line(R.string.ai_model_detail_input, it) }
        model.supportsThinking?.let {
            line(
                R.string.ai_model_detail_thinking,
                getString(if (it) R.string.ai_model_detail_thinking_yes else R.string.ai_model_detail_thinking_no),
            )
        }
        model.createdEpochSeconds?.let { line(R.string.ai_model_detail_released, formatDate(it)) }
        if (model.isDeprecated) line(R.string.ai_model_detail_status, getString(R.string.ai_model_detail_deprecated_value))
    }

/** e.g. "Text · Image · PDF"; `null` when the vendor reported neither capability. */
private fun Context.inputKinds(model: AiModelInfo): String? {
    if (model.supportsImageInput == null && model.supportsPdfInput == null) return null
    return listOfNotNull(
        getString(R.string.ai_model_input_text),
        getString(R.string.ai_model_input_image).takeIf { model.supportsImageInput == true },
        getString(R.string.ai_model_input_pdf).takeIf { model.supportsPdfInput == true },
    ).joinToString(" · ")
}

private fun Context.formatDate(epochSeconds: Long): String = DateFormat.getMediumDateFormat(this).format(Date(epochSeconds * 1000))

/** e.g. 1048576 -> "1M", 200000 -> "200K". */
private fun formatTokenCount(tokens: Long): String =
    when {
        tokens >= 1_000_000 -> "${(tokens / 1_000_000.0).roundToLong()}M"
        tokens >= 1_000 -> "${(tokens / 1_000.0).roundToLong()}K"
        else -> tokens.toString()
    }

private val AiModelTier.labelRes: Int
    get() =
        when (this) {
            AiModelTier.FAST -> R.string.ai_model_tier_fast
            AiModelTier.BALANCED -> R.string.ai_model_tier_balanced
            AiModelTier.POWERFUL -> R.string.ai_model_tier_powerful
        }

private val AiModelTier.costRes: Int
    get() =
        when (this) {
            AiModelTier.FAST -> R.string.ai_model_cost_fast
            AiModelTier.BALANCED -> R.string.ai_model_cost_balanced
            AiModelTier.POWERFUL -> R.string.ai_model_cost_powerful
        }

private val AiModelTier.qualityRes: Int
    get() =
        when (this) {
            AiModelTier.FAST -> R.string.ai_model_quality_fast
            AiModelTier.BALANCED -> R.string.ai_model_quality_balanced
            AiModelTier.POWERFUL -> R.string.ai_model_quality_powerful
        }

private val AiModelTier.useCaseRes: Int
    get() =
        when (this) {
            AiModelTier.FAST -> R.string.ai_model_use_case_fast
            AiModelTier.BALANCED -> R.string.ai_model_use_case_balanced
            AiModelTier.POWERFUL -> R.string.ai_model_use_case_powerful
        }
