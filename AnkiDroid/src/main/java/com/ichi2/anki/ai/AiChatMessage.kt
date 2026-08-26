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
