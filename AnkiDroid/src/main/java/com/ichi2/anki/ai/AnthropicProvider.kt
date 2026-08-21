// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AnthropicProvider : AiProvider {
    override val defaultModel = "claude-haiku-4-5"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request {
        val body =
            JSONObject()
                .put("model", model)
                .put("max_tokens", 1024)
                .put("stream", true)
                .put(
                    "messages",
                    JSONArray(
                        messages.map { message ->
                            JSONObject()
                                .put("role", if (message.role == AiChatRole.USER) "user" else "assistant")
                                .put("content", message.content)
                        },
                    ),
                )
        if (systemPrompt != null) {
            body.put("system", systemPrompt)
        }

        return Request
            .Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    override fun parseSseEvent(data: String): AiSseEvent =
        try {
            val json = JSONObject(data)
            when (json.optString("type")) {
                "content_block_delta" -> {
                    val text = json.getJSONObject("delta").optString("text", "")
                    if (text.isNotEmpty()) AiSseEvent.Token(text) else AiSseEvent.Ignored
                }
                "message_stop" -> AiSseEvent.Done
                else -> AiSseEvent.Ignored
            }
        } catch (e: JSONException) {
            AiSseEvent.Ignored
        }
}
