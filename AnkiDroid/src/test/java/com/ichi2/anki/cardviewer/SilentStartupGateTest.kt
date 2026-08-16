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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test

class SilentStartupGateTest {
    @After
    fun resetGate() {
        SilentStartupGate.resetForTest()
    }

    @Test
    fun `gate starts silenced`() {
        assertThat("프로세스 시작 시에는 무음 상태여야 한다", SilentStartupGate.isSilenced, equalTo(true))
    }

    @Test
    fun `volume change unsilences the gate`() {
        SilentStartupGate.onVolumeChanged()

        assertThat("볼륨 변경 후에는 무음이 해제돼야 한다", SilentStartupGate.isSilenced, equalTo(false))
    }

    @Test
    fun `onVolumeChanged returns true only on the transition`() {
        val firstCall = SilentStartupGate.onVolumeChanged()
        val secondCall = SilentStartupGate.onVolumeChanged()

        assertThat("첫 볼륨 변경은 무음 해제 전환이어야 한다", firstCall, equalTo(true))
        assertThat("이미 해제된 상태에서 또 볼륨을 바꿔도 전환은 아니다", secondCall, equalTo(false))
    }
}
