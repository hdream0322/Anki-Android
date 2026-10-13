// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import okhttp3.Request

sealed class AiSseEvent {
    data class Token(
        val text: String,
    ) : AiSseEvent()

    object Done : AiSseEvent()

    object Ignored : AiSseEvent()
}

/**
 * Where a model sits in its vendor's lineup, from the cheapest and fastest to the most capable.
 * Vendors' list APIs give no prices, so this is what the picker uses to compare cost and quality.
 */
enum class AiModelTier {
    FAST,
    BALANCED,
    POWERFUL,
}

/**
 * A model from a vendor's model list. Every field but [id] is optional because each vendor's
 * official list returns a different subset of them; `null` means "not reported".
 *
 * @param tier `null` when the model's place in the lineup cannot be told
 * @param inputTokenLimit the context window in tokens
 * @param outputTokenLimit the most tokens one reply can contain
 * @param createdEpochSeconds the release time
 * @param isDeprecated the model still works but is headed for retirement
 */
data class AiModelInfo(
    val id: String,
    val displayName: String? = null,
    val description: String? = null,
    val tier: AiModelTier? = null,
    val inputTokenLimit: Long? = null,
    val outputTokenLimit: Long? = null,
    val createdEpochSeconds: Long? = null,
    val supportsImageInput: Boolean? = null,
    val supportsPdfInput: Boolean? = null,
    val supportsThinking: Boolean? = null,
    val isDeprecated: Boolean = false,
)

/** One page of a vendor's model list; [nextPageToken] is `null` on the last page. */
data class AiModelsPage(
    val models: List<AiModelInfo>,
    val nextPageToken: String?,
)

/** Vendor-specific adapter: builds the HTTP request and parses each SSE `data:` payload. */
interface AiProvider {
    val defaultModel: String

    /** The vendor's official API pricing page; model lists carry no prices. */
    val pricingUrl: String

    fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request

    fun parseSseEvent(data: String): AiSseEvent

    /** Builds a request to the vendor's official model-list endpoint, starting after [pageToken] if given. */
    fun buildListModelsRequest(
        apiKey: String,
        pageToken: String?,
    ): Request

    /** Parses a model-list response, keeping only models usable for chat. Throws [org.json.JSONException] on malformed input. */
    fun parseModelsPage(body: String): AiModelsPage
}

/**
 * Runs [build], turning OkHttp's rejection of a key header into [AiError.MissingApiKey].
 *
 * OkHttp rejects header values with non-ASCII or control characters (e.g. a zero-width space
 * pasted along with the key) and quotes the value in the exception message for every header
 * except `Authorization`, so the original exception is dropped to keep the key out of logs and
 * crash reports.
 */
internal inline fun buildRequestWithoutLeakingKey(build: () -> Request): Request =
    try {
        build()
    } catch (e: IllegalArgumentException) {
        throw AiError.MissingApiKey
    }
