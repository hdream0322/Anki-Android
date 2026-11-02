// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import android.content.Context
import com.ichi2.anki.MetaDB
import com.ichi2.anki.libanki.Collection
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import timber.log.Timber

/**
 * AI chats used to be kept per note. MetaDB can't open the collection while upgrading,
 * so those chats are given a card here instead: the note's first card (lowest ord).
 *
 * Chats of notes that no longer exist are left unassigned, so nothing is deleted.
 */
fun Collection.assignLegacyAiChats(context: Context) {
    for (nid in MetaDB.legacyAiChatNoteIds(context)) {
        val firstCard =
            try {
                cardIdsOfNote(nid).map { getCard(it) }.minByOrNull { it.ord }
            } catch (e: BackendNotFoundException) {
                null
            } ?: continue
        Timber.i("Assigning legacy AI chat history to the note's first card")
        MetaDB.assignLegacyAiChats(context, nid = nid, cid = firstCard.id)
    }
}
