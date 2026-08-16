/*
 *  Copyright (c) 2026 AnkiDroid Open Source Team
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Intent
import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.SilentStartupGate
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/** 실제 화면 없이 [AppLifecycleObserver]를 붙일 수 있는 최소 [LifecycleOwner]. */
private class FakeLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry(this)
}

@RunWith(AndroidJUnit4::class)
class AppLifecycleObserverTest : RobolectricTest() {
    @After
    fun resetGate() {
        SilentStartupGate.resetForTest()
    }

    private fun volumeChangedIntent(streamType: Int) =
        Intent(AppLifecycleObserver.ACTION_VOLUME_CHANGED).apply {
            putExtra(AppLifecycleObserver.EXTRA_VOLUME_STREAM_TYPE, streamType)
        }

    @Test
    fun `music volume change releases the silent-startup gate and shows a toast`() {
        val observer = AppLifecycleObserver(targetContext)
        observer.onStart(FakeLifecycleOwner())

        targetContext.sendBroadcast(volumeChangedIntent(AudioManager.STREAM_MUSIC))
        advanceRobolectricLooper()

        assertThat("볼륨 변경으로 무음 게이트가 해제돼야 한다", SilentStartupGate.isSilenced, equalTo(false))
        assertThat(
            "게이트 해제 시 안내 토스트가 떠야 한다",
            ShadowToast.getTextOfLatestToast(),
            equalTo(getResourceString(R.string.silent_startup_gate_released)),
        )
    }

    @Test
    fun `non-music volume change does not release the gate`() {
        val observer = AppLifecycleObserver(targetContext)
        observer.onStart(FakeLifecycleOwner())

        targetContext.sendBroadcast(volumeChangedIntent(AudioManager.STREAM_RING))
        advanceRobolectricLooper()

        assertThat("미디어 볼륨이 아니면 게이트가 유지돼야 한다", SilentStartupGate.isSilenced, equalTo(true))
    }

    @Test
    fun `volume changes are ignored after the observer stops`() {
        val owner = FakeLifecycleOwner()
        val observer = AppLifecycleObserver(targetContext)
        observer.onStart(owner)
        observer.onStop(owner)

        targetContext.sendBroadcast(volumeChangedIntent(AudioManager.STREAM_MUSIC))
        advanceRobolectricLooper()

        assertThat("리시버가 해제된 뒤에는 게이트가 유지돼야 한다", SilentStartupGate.isSilenced, equalTo(true))
    }
}
