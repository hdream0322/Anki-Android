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

/** One page of a vendor's model list; [nextPageToken] is `null` on the last page. */
data class AiModelsPage(
    val modelIds: List<String>,
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
