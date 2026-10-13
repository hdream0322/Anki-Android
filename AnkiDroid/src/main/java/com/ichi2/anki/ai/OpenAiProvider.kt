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
    override val pricingUrl = "https://platform.openai.com/docs/pricing"

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

    // The endpoint returns every model in one unpaginated response.
    override fun buildListModelsRequest(
        apiKey: String,
        pageToken: String?,
    ): Request =
        Request
            .Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

    override fun parseModelsPage(body: String): AiModelsPage {
        val data = JSONObject(body).getJSONArray("data")
        val models = (0 until data.length()).map { data.getJSONObject(it) }
        // The list also holds embedding, audio, image, moderation and legacy `-instruct` models, none of which
        // accept chat completions, and its order is unspecified. It has no name or description,
        // so beyond the creation time all that can be told is the tier its id implies.
        val chatModels =
            models
                .filter { isChatModel(it.getString("id")) }
                .map {
                    val id = it.getString("id")
                    AiModelInfo(
                        id = id,
                        tier = tierOf(id),
                        createdEpochSeconds = it.optLong("created").takeIf { created -> created > 0 },
                    )
                }
                // Untiered ids are mostly older generations, often scheduled for shutdown, so
                // they go below the current lineup.
                .sortedWith(compareBy<AiModelInfo> { it.tier == null }.thenByDescending { it.createdEpochSeconds ?: 0 })
        return AiModelsPage(chatModels, nextPageToken = null)
    }

    /**
     * Reads the tier from the lineup codename in the id (https://developers.openai.com/api/docs/models):
     * Astra and `-pro` are the most capable, Sol and Terra sit in the middle, and Luna, `-mini`
     * and `-nano` are the cheap, fast cuts. Ids with none of these (e.g. `gpt-4o`, `gpt-3.5-turbo`,
     * plain `gpt-5`) are left untiered rather than guessed at.
     */
    private fun tierOf(id: String): AiModelTier? {
        val parts = id.split('-')
        return when {
            parts.any { it == "astra" || it == "pro" } -> AiModelTier.POWERFUL
            parts.any { it == "sol" || it == "terra" } -> AiModelTier.BALANCED
            parts.any { it == "luna" || it == "mini" || it == "nano" } -> AiModelTier.FAST
            else -> null
        }
    }

    private fun isChatModel(id: String): Boolean = CHAT_MODEL_PREFIX.containsMatchIn(id) && NON_CHAT_MARKERS.none { it in id }

    companion object {
        private val CHAT_MODEL_PREFIX = Regex("^(gpt-|chatgpt-|o\\d)")
        private val NON_CHAT_MARKERS = listOf("audio", "realtime", "tts", "transcribe", "image", "instruct")
    }
}
