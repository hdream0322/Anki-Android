// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import com.ichi2.anki.libanki.CardId

/**
 * Holds the whiteboard drawings of the last few cards which were left behind, so that undoing an
 * answer can put the undone card's drawing back on the whiteboard.
 *
 * Snapshots are strokes rather than bitmaps, so keeping a handful of them is cheap. They are held
 * in memory only: drawings aren't expected to survive leaving the reviewer.
 */
class WhiteboardSnapshotStore(
    private val maxEntries: Int = MAX_ENTRIES,
) {
    /** Snapshots by card, in insertion order: the eldest entry is discarded first. */
    private val snapshots = LinkedHashMap<CardId, Whiteboard.Snapshot>()

    /** Stores [snapshot] as the drawing of [cardId], discarding the eldest one if full. */
    fun save(
        cardId: CardId,
        snapshot: Whiteboard.Snapshot,
    ) {
        if (snapshot.isEmpty) return
        snapshots.remove(cardId)
        snapshots[cardId] = snapshot
        while (snapshots.size > maxEntries) {
            snapshots.remove(snapshots.keys.first())
        }
    }

    /** @return The drawing stored for [cardId], removing it from the store */
    fun take(cardId: CardId): Whiteboard.Snapshot? = snapshots.remove(cardId)

    companion object {
        /** Enough to cover a few undos in a row without holding onto stale drawings. */
        private const val MAX_ENTRIES = 3
    }
}
