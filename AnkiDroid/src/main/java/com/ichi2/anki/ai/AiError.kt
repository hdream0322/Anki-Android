// AnkiDroid/src/main/java/com/ichi2/anki/ai/AiError.kt
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

    object MissingApiKey : AiError("No API key configured")
}
