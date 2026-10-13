// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class GeminiProvider : AiProvider {
    override val defaultModel = "gemini-3.8-flash"

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

        // The key goes in a header rather than the query string so it cannot leak through a URL
        // embedded in an OkHttp exception message.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
        return Request
            .Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
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

    override fun buildListModelsRequest(
        apiKey: String,
        pageToken: String?,
    ): Request {
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("pageSize", MAX_PAGE_SIZE.toString())
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build()
        return Request
            .Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()
    }

    override fun parseModelsPage(body: String): AiModelsPage {
        val json = JSONObject(body)
        val models = json.optJSONArray("models") ?: JSONArray()
        val chatModels =
            (0 until models.length())
                .map { models.getJSONObject(it) }
                .filter { model ->
                    val methods = model.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                    (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                }.map { model ->
                    AiModelInfo(
                        id = model.getString("name").removePrefix("models/"),
                        displayName = model.optString("displayName").ifEmpty { null },
                        description = model.optString("description").ifEmpty { null },
                        inputTokenLimit = model.optLong("inputTokenLimit").takeIf { it > 0 },
                    )
                }
        return AiModelsPage(chatModels, json.optString("nextPageToken").ifEmpty { null })
    }

    companion object {
        private const val MAX_PAGE_SIZE = 1000
    }
}
