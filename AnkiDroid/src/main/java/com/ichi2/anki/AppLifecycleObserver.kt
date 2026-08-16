/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ichi2.anki.cardviewer.SilentStartupGate
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.widget.WidgetStatus
import timber.log.Timber

class AppLifecycleObserver(
    private val context: Context,
) : DefaultLifecycleObserver {
    /**
     * 사용자가 미디어 볼륨을 조절하면 이를 "소리를 원한다"는 명시적 의도로 보고
     * [SilentStartupGate]를 해제한다. `VOLUME_CHANGED_ACTION`은 시스템만 보낼 수 있는
     * protected broadcast이므로 [ContextCompat.RECEIVER_NOT_EXPORTED]로 등록한다.
     */
    private val volumeChangeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: Context,
                intent: Intent,
            ) {
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (streamType != AudioManager.STREAM_MUSIC) return
                if (SilentStartupGate.onVolumeChanged()) {
                    showThemedToast(receiverContext, R.string.silent_startup_gate_released, shortLength = true)
                }
            }
        }

    private var volumeChangeReceiverRegistered = false

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        if (volumeChangeReceiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            volumeChangeReceiver,
            IntentFilter(ACTION_VOLUME_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        volumeChangeReceiverRegistered = true
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)

        if (volumeChangeReceiverRegistered) {
            context.unregisterReceiver(volumeChangeReceiver)
            volumeChangeReceiverRegistered = false
        }

        if (owner.lifecycle.currentState != Lifecycle.State.DESTROYED && CollectionManager.isOpenUnsafe()) {
            try {
                WidgetStatus.updateInBackground(context)
            } catch (e: Exception) {
                Timber.w(e)
            }
        }
    }

    companion object {
        // AudioManager.VOLUME_CHANGED_ACTION / EXTRA_VOLUME_STREAM_TYPE는 SDK stub에서
        // 숨겨져 있어 컴파일 타임에 참조할 수 없다. 문자열 값은 AOSP AudioManager 공개 문서 기준.
        internal const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        internal const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
