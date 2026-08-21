// AnkiDroid/src/main/java/com/ichi2/anki/ai/chat/AiChatViewModel.kt
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

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
    val errorFlow = _errorFlow

    fun sendMessage(text: String) {
        val userMessage = AiChatMessage(AiChatRole.USER, text)
        appendAndPersist(userMessage)

        viewModelScope.launch {
            var assistantText = ""
            var hasReceivedToken = false
            streamingClient
                .stream(provider, apiKey, model, cardContent, _messages.value)
                .catch { throwable ->
                    _errorFlow.emit(throwable as? AiError ?: AiError.Network(throwable))
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
}
