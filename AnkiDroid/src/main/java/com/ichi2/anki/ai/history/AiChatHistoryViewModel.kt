// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatSummary
import com.ichi2.anki.libanki.NoteId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** One row of the AI chat history list. */
data class AiChatHistoryItem(
    val nid: NoteId,
    /** Plain-text front of the note's first card. */
    val title: String,
    val messageCount: Int,
    val lastMessage: AiChatMessage,
    /** Epoch millis of [lastMessage]. */
    val lastAt: Long,
)

/**
 * Lists the notes that have AI chat history, most recently active first.
 *
 * Chat history lives in MetaDB and outlives its note, so notes [resolveTitles] can't find
 * are hidden rather than deleted: removing someone's chats is left to an explicit action.
 */
class AiChatHistoryViewModel(
    private val loadSummaries: suspend () -> List<AiChatSummary>,
    /** Maps note ids to their titles, omitting notes that no longer exist. */
    private val resolveTitles: suspend (List<NoteId>) -> Map<NoteId, String>,
) : ViewModel() {
    private val _items = MutableStateFlow<List<AiChatHistoryItem>?>(null)

    /** `null` until the first load finishes, so the empty state isn't flashed while loading. */
    val items: StateFlow<List<AiChatHistoryItem>?> = _items.asStateFlow()

    init {
        refresh()
    }

    private var refreshJob: Job? = null

    /** Reloads the list. A newer refresh supersedes an unfinished one, so stale results never land last. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                val items =
                    try {
                        val summaries = loadSummaries()
                        val titles = resolveTitles(summaries.map { it.nid })
                        summaries.mapNotNull { summary ->
                            val title = titles[summary.nid] ?: return@mapNotNull null
                            AiChatHistoryItem(summary.nid, title, summary.messageCount, summary.lastMessage, summary.lastAt)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Keep showing the previous list rather than crashing the screen
                        Timber.w(e, "Failed to load AI chat history")
                        return@launch
                    }
                _items.value = items
            }
    }
}
