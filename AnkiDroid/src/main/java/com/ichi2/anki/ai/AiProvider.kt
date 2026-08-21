// AnkiDroid/src/main/java/com/ichi2/anki/ai/AiProvider.kt
package com.ichi2.anki.ai

import okhttp3.Request

sealed class AiSseEvent {
    data class Token(
        val text: String,
    ) : AiSseEvent()

    object Done : AiSseEvent()

    object Ignored : AiSseEvent()
}

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
}
