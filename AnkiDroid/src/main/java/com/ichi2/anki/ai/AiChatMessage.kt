// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

enum class AiChatRole {
    USER,
    ASSISTANT,
    ;

    companion object {
        fun fromStorageValue(value: String): AiChatRole = valueOf(value)
    }

    val storageValue: String get() = name
}

data class AiChatMessage(
    val role: AiChatRole,
    val content: String,
)

/** One note's AI chat history, condensed for the history list. */
data class AiChatSummary(
    val nid: Long,
    val messageCount: Int,
    val lastMessage: AiChatMessage,
    /** Epoch millis of [lastMessage]. */
    val lastAt: Long,
)
