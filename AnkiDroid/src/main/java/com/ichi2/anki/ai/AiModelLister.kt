// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fetches the models a key can use from the vendor's official model-list endpoint. */
open class AiModelLister(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build(),
) {
    /**
     * Returns every chat-capable model id across all pages, in the order [AiProvider.parseModelsPage]
     * gives them, without duplicates.
     *
     * @throws AiError on a missing key, network failure, error status or malformed response
     */
    open suspend fun listModels(
        provider: AiProvider,
        apiKey: String,
    ): List<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw AiError.MissingApiKey
            val modelIds = LinkedHashSet<String>()
            var pageToken: String? = null
            // Bounded so a vendor that keeps returning a token cannot loop forever.
            repeat(MAX_PAGES) {
                val page = fetchPage(provider, apiKey, pageToken)
                modelIds += page.modelIds
                pageToken = page.nextPageToken ?: return@withContext modelIds.toList()
            }
            modelIds.toList()
        }

    private fun fetchPage(
        provider: AiProvider,
        apiKey: String,
        pageToken: String?,
    ): AiModelsPage {
        val request = buildRequestWithoutLeakingKey { provider.buildListModelsRequest(apiKey, pageToken) }
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    response.code == HTTP_TOO_MANY_REQUESTS ->
                        throw AiError.RateLimited(response.header("Retry-After")?.toIntOrNull())
                    !response.isSuccessful -> throw AiError.Http(response.code, body)
                }
                return try {
                    provider.parseModelsPage(body)
                } catch (e: JSONException) {
                    throw AiError.Http(response.code, "Malformed model list: ${e.message}")
                }
            }
        } catch (e: IOException) {
            throw AiError.Network(e)
        }
    }

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_PAGES = 20
        private const val CALL_TIMEOUT_SECONDS = 30L
    }
}
