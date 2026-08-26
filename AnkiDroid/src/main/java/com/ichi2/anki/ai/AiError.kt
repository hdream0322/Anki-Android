// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

sealed class AiError(
    message: String,
) : Exception(message) {
    class Network(
        cause: Throwable,
    ) : AiError("Network error: ${cause.message}")

    class Http(
        val code: Int,
        body: String,
    ) : AiError("HTTP $code: $body")

    /** HTTP 429, split out from [Http] so callers can offer a wait-and-retry message. */
    class RateLimited(
        val retryAfterSeconds: Int?,
    ) : AiError("Rate limited")

    object MissingApiKey : AiError("No API key configured")
}
