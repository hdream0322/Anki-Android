// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.AiError
import com.ichi2.anki.ai.AiProvider
import com.ichi2.anki.ai.AiSseEvent
import com.ichi2.anki.ai.AiStreamingClient
import com.ichi2.anki.libanki.NoteId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber

class AiChatViewModel(
    private val noteId: NoteId,
    private val cardContent: String,
    private val provider: AiProvider,
    private val apiKey: String,
    private val model: String,
    private val streamingClient: AiStreamingClient,
    private val storeMessage: (AiChatMessage) -> Unit,
    private val loadHistory: () -> List<AiChatMessage>,
) : ViewModel() {
    private val _messages = MutableStateFlow(loadHistory())
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _errorFlow = MutableSharedFlow<AiError>()
    val errorFlow = _errorFlow.asSharedFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    fun sendMessage(text: String) {
        if (_isStreaming.value) return

        val userMessage = AiChatMessage(AiChatRole.USER, text)
        appendAndPersist(userMessage)

        _isStreaming.value = true
        viewModelScope.launch {
            try {
                var assistantText = ""
                var hasReceivedToken = false
                streamingClient
                    .stream(provider, apiKey, model, cardContent, buildRequestHistory(_messages.value))
                    .catch { throwable ->
                        val error = throwable as? AiError ?: AiError.Network(throwable)
                        Timber.w(error, "AI chat request failed")
                        _errorFlow.emit(error)
                    }.collect { event ->
                        when (event) {
                            is AiSseEvent.Token -> {
                                assistantText += event.text
                                hasReceivedToken = true
                                replaceStreamingAssistantMessage(assistantText)
                            }
                            AiSseEvent.Done -> {
                                if (hasReceivedToken) {
                                    storeMessage(AiChatMessage(AiChatRole.ASSISTANT, assistantText))
                                }
                            }
                            AiSseEvent.Ignored -> {}
                        }
                    }
            } finally {
                _isStreaming.value = false
            }
        }
    }

    private fun appendAndPersist(message: AiChatMessage) {
        _messages.value = _messages.value + message
        storeMessage(message)
    }

    private fun replaceStreamingAssistantMessage(text: String) {
        val current = _messages.value
        val last = current.lastOrNull()
        _messages.value =
            if (last?.role == AiChatRole.ASSISTANT) {
                current.dropLast(1) + AiChatMessage(AiChatRole.ASSISTANT, text)
            } else {
                current + AiChatMessage(AiChatRole.ASSISTANT, text)
            }
    }

    companion object {
        /** Upper bound on how many past messages accompany a request, to cap the user's API spend. */
        const val MAX_HISTORY_MESSAGES = 20

        /**
         * Shapes the stored history into a payload the providers accept.
         *
         * Anthropic and Gemini reject histories that do not strictly alternate roles starting with
         * the user, which a failed request leaves behind: the user turn is persisted but the
         * assistant reply never is.
         */
        fun buildRequestHistory(messages: List<AiChatMessage>): List<AiChatMessage> =
            collapseConsecutiveSameRole(messages)
                .takeLast(MAX_HISTORY_MESSAGES)
                .dropWhile { it.role != AiChatRole.USER }

        /** Merges each run of consecutive same-role messages into a single message. */
        fun collapseConsecutiveSameRole(messages: List<AiChatMessage>): List<AiChatMessage> =
            messages.fold(mutableListOf<AiChatMessage>()) { acc, message ->
                val previous = acc.lastOrNull()
                if (previous?.role == message.role) {
                    acc[acc.lastIndex] = AiChatMessage(message.role, previous.content + "\n\n" + message.content)
                } else {
                    acc.add(message)
                }
                acc
            }
    }
}
