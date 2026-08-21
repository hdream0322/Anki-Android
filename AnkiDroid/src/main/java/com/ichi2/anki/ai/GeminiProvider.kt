package com.ichi2.anki.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class GeminiProvider : AiProvider {
    override val defaultModel = "gemini-2.0-flash"

    override fun buildRequest(
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Request {
        val body =
            JSONObject()
                .put(
                    "contents",
                    JSONArray(
                        messages.map { message ->
                            JSONObject()
                                .put("role", if (message.role == AiChatRole.USER) "user" else "model")
                                .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
                        },
                    ),
                )
        if (systemPrompt != null) {
            body.put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        return Request
            .Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    override fun parseSseEvent(data: String): AiSseEvent =
        try {
            val json = JSONObject(data)
            val candidates = json.getJSONArray("candidates")
            val candidate = candidates.getJSONObject(0)
            val text =
                candidate
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .optString("text", "")
            if (text.isNotEmpty()) AiSseEvent.Token(text) else AiSseEvent.Ignored
        } catch (e: JSONException) {
            AiSseEvent.Ignored
        }
}
