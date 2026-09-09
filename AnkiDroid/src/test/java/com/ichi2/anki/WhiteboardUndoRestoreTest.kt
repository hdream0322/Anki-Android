// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.os.SystemClock
import android.view.MotionEvent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.CardId
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for restoring a card's whiteboard drawing when its answer is undone:
 * accidentally answering a card and then undoing it must bring the drawing back.
 */
@RunWith(AndroidJUnit4::class)
class WhiteboardUndoRestoreTest : RobolectricTest() {
    /** [MetaDB], which stores the whiteboard's enabled state, needs a collection with a path. */
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    private fun startReviewerWithWhiteboard(cardCount: Int): Reviewer {
        for (i in 0 until cardCount) {
            addBasicNote(front = "front $i")
        }
        val reviewer = ReviewerTest.startReviewer(this)
        advanceRobolectricLooper()
        reviewer.toggleWhiteboard()
        advanceRobolectricLooper()
        return reviewer
    }

    private fun drawStroke(whiteboard: Whiteboard) {
        val downTime = SystemClock.uptimeMillis()
        whiteboard.handleTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        whiteboard.handleTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, 10f, 100f, 0))
        whiteboard.handleTouchEvent(MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_UP, 10f, 100f, 0))
    }

    private fun Reviewer.hasDrawing() = !whiteboard!!.undoEmpty()

    @Test
    fun `drawing is restored when the answer is undone`() =
        runTest {
            val reviewer = startReviewerWithWhiteboard(cardCount = 2)
            drawStroke(reviewer.whiteboard!!)
            assertThat("the card was drawn on", reviewer.hasDrawing(), equalTo(true))

            reviewer.answerCard(Rating.GOOD)
            advanceRobolectricLooper()
            assertThat("the next card starts with a blank whiteboard", reviewer.hasDrawing(), equalTo(false))

            reviewer.undo()
            advanceRobolectricLooper()
            assertThat("the undone card's drawing is restored", reviewer.hasDrawing(), equalTo(true))
        }

    @Test
    fun `drawing is not restored when the preference is off`() =
        runTest {
            val reviewer = startReviewerWithWhiteboard(cardCount = 2)
            reviewer.sharedPrefs().edit {
                putBoolean(reviewer.getString(R.string.whiteboard_undo_restore_key), false)
            }
            drawStroke(reviewer.whiteboard!!)

            reviewer.answerCard(Rating.GOOD)
            advanceRobolectricLooper()
            reviewer.undo()
            advanceRobolectricLooper()

            assertThat("the drawing stays gone", reviewer.hasDrawing(), equalTo(false))
        }

    @Test
    fun `drawing is not restored on a normal card change`() =
        runTest {
            val reviewer = startReviewerWithWhiteboard(cardCount = 3)
            drawStroke(reviewer.whiteboard!!)

            reviewer.answerCard(Rating.GOOD)
            advanceRobolectricLooper()
            reviewer.answerCard(Rating.GOOD)
            advanceRobolectricLooper()

            assertThat("each new card starts with a blank whiteboard", reviewer.hasDrawing(), equalTo(false))
        }

    @Test
    fun `snapshot is restored onto the whiteboard it was taken from`() =
        runTest {
            val reviewer = startReviewerWithWhiteboard(cardCount = 1)
            val whiteboard = reviewer.whiteboard!!
            drawStroke(whiteboard)

            val snapshot = whiteboard.takeSnapshot()
            whiteboard.clear()
            assertThat("the whiteboard was cleared", whiteboard.undoEmpty(), equalTo(true))

            whiteboard.restoreSnapshot(snapshot)
            assertThat("the strokes are back", whiteboard.undoEmpty(), equalTo(false))
        }

    @Test
    fun `store keeps only the most recent cards`() =
        runTest {
            val reviewer = startReviewerWithWhiteboard(cardCount = 1)
            val whiteboard = reviewer.whiteboard!!
            drawStroke(whiteboard)
            val snapshot = whiteboard.takeSnapshot()

            val store = WhiteboardSnapshotStore(maxEntries = 3)
            val cardIds = (1L..4L).toList()
            for (cardId in cardIds) {
                store.save(cardId, snapshot)
            }

            assertThat("the oldest card was evicted", store.take(cardIds.first()), equalTo(null))
            for (cardId in cardIds.drop(1)) {
                assertThat("card $cardId is kept", store.take(cardId), equalTo(snapshot))
            }
        }

    @Test
    fun `a restored snapshot is not restored twice`() =
        runTest {
            val reviewer = startReviewerWithWhiteboard(cardCount = 1)
            val whiteboard = reviewer.whiteboard!!
            drawStroke(whiteboard)

            val store = WhiteboardSnapshotStore()
            val cardId: CardId = 1
            store.save(cardId, whiteboard.takeSnapshot())

            assertThat("the snapshot is returned once", store.take(cardId), not(equalTo(null)))
            assertThat("and then consumed", store.take(cardId), equalTo(null))
        }

    @Test
    fun `an empty whiteboard is not stored`() =
        runTest {
            val reviewer = startReviewerWithWhiteboard(cardCount = 1)
            val store = WhiteboardSnapshotStore()

            store.save(cardId = 1, snapshot = reviewer.whiteboard!!.takeSnapshot())

            assertThat("nothing to restore", store.take(1), equalTo(null))
        }
}
