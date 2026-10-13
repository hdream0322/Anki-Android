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
 * A model from a vendor's model list. Every field but [id] is optional because each vendor's
 * official list returns a different subset of them.
 *
 * @param inputTokenLimit the context window in tokens
 * @param createdEpochSeconds the release time, used when the vendor gives nothing more descriptive
 * @param isDeprecated the model still works but is headed for retirement
 */
data class AiModelInfo(
    val id: String,
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Long? = null,
    val createdEpochSeconds: Long? = null,
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
