// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class OpenAiProvider : AiProvider {
    override val defaultModel = "gpt-5.6-luna"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request {
        val allMessages = mutableListOf<JSONObject>()
        if (systemPrompt != null) {
            allMessages += JSONObject().put("role", "system").put("content", systemPrompt)
        }
        messages.forEach { message ->
            allMessages +=
                JSONObject()
                    .put("role", if (message.role == AiChatRole.USER) "user" else "assistant")
                    .put("content", message.content)
        }

        val body =
            JSONObject()
                .put("model", model)
                .put("stream", true)
                .put("messages", JSONArray(allMessages))

        return Request
            .Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    override fun parseSseEvent(data: String): AiSseEvent {
        if (data == "[DONE]") return AiSseEvent.Done
        return try {
            val delta =
                JSONObject(data)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("delta")
            val content = delta.optString("content", "")
            if (content.isNotEmpty()) AiSseEvent.Token(content) else AiSseEvent.Ignored
        } catch (e: JSONException) {
            AiSseEvent.Ignored
        }
    }
}
