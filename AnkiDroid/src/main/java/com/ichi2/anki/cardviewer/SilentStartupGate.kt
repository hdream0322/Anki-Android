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

import androidx.annotation.VisibleForTesting

/**
 * 앱이 뜰 때 미디어 볼륨이 이미 크게 설정돼 있으면 효과음이 갑자기 크게 나서
 * 놀라는 문제를 막기 위한 게이트.
 *
 * 프로세스가 새로 뜨면 항상 무음 상태([isSilenced] == true)로 시작한다. 사용자가
 * 실제로 미디어 볼륨을 조절하면([onVolumeChanged]) 그 순간을 "소리를 원한다"는
 * 명시적 의도로 간주해 무음을 해제한다.
 *
 * 이 상태는 의도적으로 SharedPreferences 등에 저장하지 않는다. 저장하면 콜드
 * 스타트마다 다시 조심스럽게 시작한다는 취지가 깨지기 때문이다. 프로세스 생명에만
 * 묶인 값이므로, 콜드 스타트 시엔 자동으로 무음으로 리셋되고, 백그라운드에
 * 갔다가(프로세스가 죽지 않고) 돌아오는 경우엔 상태가 그대로 유지된다.
 */
object SilentStartupGate {
    @Volatile
    var isSilenced: Boolean = true
        private set

    /**
     * 무음이 해제되는 순간 호출되는 콜백. SFX가 실제로 재생되는 화면(Reviewer)이 이 콜백을
     * 등록해 두면, 해제 안내를 그 화면의 UI 컨벤션(Snackbar)으로 보여줄 수 있다. 등록된
     * 화면이 없으면(예: 리뷰 화면 밖에서 볼륨을 조절한 경우) 아무 것도 표시하지 않는다.
     */
    @Volatile
    private var releaseListener: (() -> Unit)? = null

    /**
     * 사용자가 미디어 볼륨을 조절했을 때 호출한다.
     * @return 이 호출로 무음이 해제됐으면(전환이 일어났으면) true, 이미 해제된 상태였으면 false.
     */
    @Synchronized
    fun onVolumeChanged(): Boolean {
        if (!isSilenced) return false
        isSilenced = false
        releaseListener?.invoke()
        return true
    }

    fun setReleaseListener(listener: (() -> Unit)?) {
        releaseListener = listener
    }

    @VisibleForTesting
    internal fun resetForTest() {
        isSilenced = true
        releaseListener = null
    }
}
