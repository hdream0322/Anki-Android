// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/** Streams tokens from an [AiProvider] over Server-Sent Events using OkHttp's `okhttp-sse`. */
open class AiStreamingClient(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build(),
) {
    /**
     * Streams a single reply, transparently retrying a connection that fails before any token
     * arrives. A failure after streaming has started is surfaced immediately instead, since
     * replaying the request at that point risks a duplicated or contradictory reply.
     */
    open fun stream(
        provider: AiProvider,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Flow<AiSseEvent> =
        flow {
            var attempt = 0
            while (true) {
                var tokenSeen = false
                try {
                    connect(provider, apiKey, model, systemPrompt, messages).collect { event ->
                        if (event is AiSseEvent.Token) tokenSeen = true
                        emit(event)
                    }
                    return@flow
                } catch (e: AiError) {
                    if (tokenSeen) throw e
                    val delayMillis = retryDelayMillis(e, attempt) ?: throw e
                    attempt++
                    delay(delayMillis)
                }
            }
        }

    private fun connect(
        provider: AiProvider,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Flow<AiSseEvent> =
        callbackFlow {
            var sawDone = false
            val request = provider.buildRequest(apiKey, model, systemPrompt, messages)
            val factory = EventSources.createFactory(client)
            val listener =
                object : EventSourceListener() {
                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        when (val event = provider.parseSseEvent(data)) {
                            AiSseEvent.Ignored -> {}
                            else -> {
                                if (event == AiSseEvent.Done) sawDone = true
                                trySend(event)
                            }
                        }
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        val error =
                            when {
                                response != null && response.code == HTTP_TOO_MANY_REQUESTS ->
                                    AiError.RateLimited(response.header("Retry-After")?.toIntOrNull())
                                response != null && !response.isSuccessful ->
                                    AiError.Http(response.code, response.body.string())
                                else -> AiError.Network(t ?: Exception("Unknown streaming failure"))
                            }
                        close(error)
                    }

                    override fun onClosed(eventSource: EventSource) {
                        if (!sawDone) trySend(AiSseEvent.Done)
                        close()
                    }
                }
            val eventSource = factory.newEventSource(request, listener)
            awaitClose { eventSource.cancel() }
        }.buffer(Channel.UNLIMITED)

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_TRANSIENT_RETRIES = 2
        private const val MAX_RATE_LIMIT_RETRIES = 1
        private const val DEFAULT_RATE_LIMIT_WAIT_SECONDS = 5
        private const val MAX_RATE_LIMIT_WAIT_SECONDS = 30

        private fun backoffMillis(attempt: Int): Long = 1000L * (attempt + 1)

        /** The delay before retrying [error], or `null` if it should be surfaced instead. */
        private fun retryDelayMillis(
            error: AiError,
            attempt: Int,
        ): Long? =
            when (error) {
                is AiError.Network -> if (attempt < MAX_TRANSIENT_RETRIES) backoffMillis(attempt) else null
                is AiError.Http -> if (error.code in 500..599 && attempt < MAX_TRANSIENT_RETRIES) backoffMillis(attempt) else null
                is AiError.RateLimited ->
                    if (attempt < MAX_RATE_LIMIT_RETRIES) {
                        (error.retryAfterSeconds ?: DEFAULT_RATE_LIMIT_WAIT_SECONDS)
                            .coerceIn(0, MAX_RATE_LIMIT_WAIT_SECONDS)
                            .toLong() * 1000L
                    } else {
                        null
                    }
                AiError.MissingApiKey -> null
            }
    }
}
