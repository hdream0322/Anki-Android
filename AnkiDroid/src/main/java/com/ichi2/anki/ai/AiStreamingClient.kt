// AnkiDroid/src/main/java/com/ichi2/anki/ai/AiStreamingClient.kt
package com.ichi2.anki.ai

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/** Streams tokens from an [AiProvider] over Server-Sent Events using OkHttp's `okhttp-sse`. */
class AiStreamingClient(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build(),
) {
    fun stream(
        provider: AiProvider,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<AiChatMessage>,
    ): Flow<AiSseEvent> =
        callbackFlow {
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
                            else -> trySend(event)
                        }
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        val error =
                            if (response != null && !response.isSuccessful) {
                                AiError.Http(response.code, response.body.string())
                            } else {
                                AiError.Network(t ?: Exception("Unknown streaming failure"))
                            }
                        close(error)
                    }

                    override fun onClosed(eventSource: EventSource) {
                        close()
                    }
                }
            val eventSource = factory.newEventSource(request, listener)
            awaitClose { eventSource.cancel() }
        }
}
