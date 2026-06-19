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
package com.ichi2.anki.cardviewer

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowMediaPlayer

@RunWith(AndroidJUnit4::class)
class SoundEffectPlayerTest : RobolectricTest() {
    @Before
    fun enableSoundEffects() {
        // 모든 데이터 소스에 대해 1ms 길이의 가짜 미디어를 제공한다.
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1, 0) }
        targetContext.sharedPrefs().edit {
            putBoolean(targetContext.getString(R.string.sound_effects_enabled_key), true)
            putBoolean(targetContext.getString(R.string.sound_effect_correct_key), true)
            putBoolean(targetContext.getString(R.string.sound_effect_applause_key), true)
        }
    }

    @Test
    fun `applause is deferred until the correct sound finishes`() {
        val player = SoundEffectPlayer(targetContext)
        player.playCorrect()
        player.playApplause()

        // 정답 효과음이 아직 재생 중이므로 박수는 시작되지 않아야 한다 (소리가 겹쳐 잘리는 것을 방지).
        assertThat("박수는 정답 효과음 재생 중에는 시작되면 안 된다", player.applauseStartCount, equalTo(0))

        // 정답 효과음 재생이 끝나면 박수가 시작된다.
        advanceRobolectricLooper()
        assertThat("정답 효과음이 끝난 뒤 박수가 시작돼야 한다", player.applauseStartCount, equalTo(1))
    }

    @Test
    fun `applause plays immediately when no correct sound is playing`() {
        val player = SoundEffectPlayer(targetContext)
        player.playApplause()

        assertThat(player.applauseStartCount, equalTo(1))
    }
}
