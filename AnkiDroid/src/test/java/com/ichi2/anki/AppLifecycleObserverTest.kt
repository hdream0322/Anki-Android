// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.SilentStartupGate
import com.ichi2.widget.WidgetStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class AppLifecycleObserverTest : RobolectricTest() {
    private val owner = TestLifecycleOwner()

    @Before
    fun mockWidgetUpdates() {
        mockkObject(WidgetStatus)
        every { WidgetStatus.updateInBackground(any()) } just runs
    }

    @After
    fun cleanUp() {
        owner.registry.currentState = Lifecycle.State.DESTROYED
        unmockkObject(CollectionManager, WidgetStatus)
        SilentStartupGate.resetForTest()
    }

    /** Issue 19749: blocking on a sync here prevents WorkManager's service timeout callback. */
    @Test
    fun `backgrounding during sync does not wait for the collection queue`() =
        runTest {
            CollectionManager.ensureOpen()
            val syncFinished = CompletableDeferred<Unit>()
            mockkObject(CollectionManager)
            coEvery { CollectionManager.withOpenColOrNull<Boolean>(any()) } coAnswers {
                syncFinished.await()
                true
            }

            AppLifecycleObserver(targetContext).onStop(owner)
            runCurrent()
            verify(exactly = 0) { WidgetStatus.updateInBackground(any()) }

            syncFinished.complete(Unit)
            advanceUntilIdle()
            verify(exactly = 1) { WidgetStatus.updateInBackground(targetContext) }
        }

    @Test
    fun `backgrounding with a closed collection does not open it or update widgets`() =
        runTest {
            CollectionManager.ensureClosed()
            AppLifecycleObserver(targetContext).onStop(owner)
            advanceUntilIdle()

            verify(exactly = 0) { WidgetStatus.updateInBackground(any()) }
            assertFalse(CollectionManager.isOpenUnsafe())
        }

    private fun volumeChangedIntent(streamType: Int) =
        Intent(AppLifecycleObserver.ACTION_VOLUME_CHANGED).apply {
            putExtra(AppLifecycleObserver.EXTRA_VOLUME_STREAM_TYPE, streamType)
        }

    @Test
    fun `music volume change releases the silent-startup gate`() {
        val observer = AppLifecycleObserver(targetContext)
        observer.onStart(owner)

        targetContext.sendBroadcast(volumeChangedIntent(AudioManager.STREAM_MUSIC))
        advanceRobolectricLooper()

        assertThat("볼륨 변경으로 무음 게이트가 해제돼야 한다", SilentStartupGate.isSilenced, equalTo(false))
    }

    @Test
    fun `gate release does not show a system toast directly`() {
        // AppLifecycleObserver는 화면(View)이 없는 프로세스 전역 컴포넌트이므로 시스템 Toast를
        // 직접 띄우지 않는다. 해제 안내는 SilentStartupGate에 등록된 리스너(Reviewer의 Snackbar)를
        // 통해서만 표시된다.
        val observer = AppLifecycleObserver(targetContext)
        observer.onStart(owner)

        targetContext.sendBroadcast(volumeChangedIntent(AudioManager.STREAM_MUSIC))
        advanceRobolectricLooper()

        assertThat("AppLifecycleObserver가 직접 시스템 토스트를 띄우면 안 된다", ShadowToast.getTextOfLatestToast(), nullValue())
    }

    @Test
    fun `gate release invokes a registered listener instead of a toast`() {
        var listenerInvoked = false
        SilentStartupGate.setReleaseListener { listenerInvoked = true }

        val observer = AppLifecycleObserver(targetContext)
        observer.onStart(owner)

        targetContext.sendBroadcast(volumeChangedIntent(AudioManager.STREAM_MUSIC))
        advanceRobolectricLooper()

        assertThat("등록된 리스너를 통해 해제 안내가 전달돼야 한다", listenerInvoked, equalTo(true))
    }

    @Test
    fun `non-music volume change does not release the gate`() {
        val observer = AppLifecycleObserver(targetContext)
        observer.onStart(owner)

        targetContext.sendBroadcast(volumeChangedIntent(AudioManager.STREAM_RING))
        advanceRobolectricLooper()

        assertThat("미디어 볼륨이 아니면 게이트가 유지돼야 한다", SilentStartupGate.isSilenced, equalTo(true))
    }

    @Test
    fun `volume changes are ignored after the observer stops`() {
        val observer = AppLifecycleObserver(targetContext)
        observer.onStart(owner)
        observer.onStop(owner)

        targetContext.sendBroadcast(volumeChangedIntent(AudioManager.STREAM_MUSIC))
        advanceRobolectricLooper()

        assertThat("리시버가 해제된 뒤에는 게이트가 유지돼야 한다", SilentStartupGate.isSilenced, equalTo(true))
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }

        override val lifecycle: Lifecycle get() = registry
    }
}
