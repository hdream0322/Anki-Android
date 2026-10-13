// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

class AnthropicProvider : AiProvider {
    override val defaultModel = "claude-haiku-5-5"
    override val pricingUrl = "https://platform.claude.com/docs/en/about-claude/pricing"

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

    override fun buildListModelsRequest(
        apiKey: String,
        pageToken: String?,
    ): Request {
        val url =
            "https://api.anthropic.com/v1/models"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("limit", MAX_PAGE_SIZE.toString())
                .apply { if (pageToken != null) addQueryParameter("after_id", pageToken) }
                .build()
        return Request
            .Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()
    }

    // The API already lists the newest models first, so its order is kept.
    override fun parseModelsPage(body: String): AiModelsPage {
        val json = JSONObject(body)
        val data = json.getJSONArray("data")
        val models =
            (0 until data.length()).map {
                val model = data.getJSONObject(it)
                val capabilities = model.optJSONObject("capabilities")
                AiModelInfo(
                    id = model.getString("id"),
                    displayName = model.optString("display_name").ifEmpty { null },
                    tier = LINE_TIERS[model.optString("line")],
                    inputTokenLimit = model.optLong("max_input_tokens").takeIf { limit -> limit > 0 },
                    outputTokenLimit = model.optLong("max_tokens").takeIf { limit -> limit > 0 },
                    // The API sends the epoch when the release date is unknown.
                    createdEpochSeconds = parseEpochSeconds(model.optString("created_at"))?.takeIf { time -> time > 0 },
                    supportsImageInput = capabilities?.isSupported("image_input"),
                    supportsPdfInput = capabilities?.isSupported("pdf_input"),
                    supportsThinking = capabilities?.isSupported("thinking"),
                    isDeprecated = model.optString("lifecycle") == "deprecated",
                )
            }
        val nextPageToken = if (json.optBoolean("has_more")) json.optString("last_id").ifEmpty { null } else null
        return AiModelsPage(models, nextPageToken)
    }

    private fun JSONObject.isSupported(capability: String): Boolean? = optJSONObject(capability)?.optBoolean("supported")

    private fun parseEpochSeconds(rfc3339: String): Long? =
        try {
            Instant.parse(rfc3339).epochSecond
        } catch (e: DateTimeParseException) {
            null
        }

    companion object {
        private const val MAX_PAGE_SIZE = 1000

        /** Only the documented lines; the API says not to infer a line, so others get no tier. */
        private val LINE_TIERS =
            mapOf(
                "haiku" to AiModelTier.FAST,
                "sonnet" to AiModelTier.BALANCED,
                "opus" to AiModelTier.POWERFUL,
            )
    }
}
