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

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import timber.log.Timber

/**
 * 학습 중 재생되는 효과음(SFX) 플레이어.
 *
 * 각 효과음은 `res/raw`의 음원이며, 재생할 때마다 독립적인 [MediaPlayer]를 만들고
 * 재생이 끝나면 스스로 해제한다. [applicationContext][Context.getApplicationContext]를
 * 사용하므로 학습 화면이 닫히는 시점(예: 덱 완료 시 박수 소리)에도 재생이 끊기지 않는다.
 *
 * 재생 여부는 설정의 마스터 스위치([R.string.sound_effects_enabled_key])와
 * 각 효과음별 개별 스위치로 제어된다. 둘 중 하나라도 꺼져 있으면 재생되지 않는다.
 */
class SoundEffectPlayer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    /** 좋음(Good) 버튼: 정답 효과음 */
    fun playCorrect() = playIfEnabled(R.string.sound_effect_correct_key, R.raw.sfx_correct)

    /** 다시(Again) 버튼: 오답 효과음 */
    fun playError() = playIfEnabled(R.string.sound_effect_error_key, R.raw.sfx_error)

    /** leech 카드 또는 다시/어려움 5회 연속 누적 시: 낙담 효과음 */
    fun playAwwMan() = playIfEnabled(R.string.sound_effect_aww_man_key, R.raw.sfx_aww_man)

    /**
     * 오답(error) 효과음을 재생한 뒤 이어서 낙담(aww-man) 효과음을 순차 재생한다.
     * 5번째 '다시'를 누르는 순간처럼 두 효과음이 동시에 발생하는 경우에 사용한다.
     * 각 효과음은 개별 설정에 따라 생략될 수 있다.
     */
    fun playErrorThenAwwMan() {
        val errorEnabled = isEnabled(R.string.sound_effect_error_key)
        val awwEnabled = isEnabled(R.string.sound_effect_aww_man_key)
        when {
            errorEnabled && awwEnabled -> play(R.raw.sfx_error) { play(R.raw.sfx_aww_man) }
            errorEnabled -> play(R.raw.sfx_error)
            awwEnabled -> play(R.raw.sfx_aww_man)
        }
    }

    /**
     * 덱 학습 완료 시 박수 소리. 음원은 약 26초이므로 약 5초 재생 후
     * 디졸브(페이드아웃)로 자연스럽게 소리를 줄여 멈춘다.
     */
    fun playApplause() {
        if (!isEnabled(R.string.sound_effect_applause_key)) return
        val mp = create(R.raw.sfx_applause) ?: return
        // 5초보다 짧을 경우를 대비해 완료 리스너로도 해제
        mp.setOnCompletionListener { release(it) }
        try {
            mp.start()
        } catch (e: Exception) {
            Timber.w(e, "failed to start applause")
            release(mp)
            return
        }
        scheduleApplauseFadeOut(mp)
    }

    private fun scheduleApplauseFadeOut(mp: MediaPlayer) {
        handler.postDelayed(
            object : Runnable {
                var step = 0

                override fun run() {
                    if (!isAlive(mp)) return
                    val volume = (1f - step / FADE_STEPS.toFloat()).coerceIn(0f, 1f)
                    try {
                        mp.setVolume(volume, volume)
                    } catch (e: IllegalStateException) {
                        Timber.w(e, "applause fade-out failed; releasing")
                        release(mp)
                        return
                    }
                    if (step >= FADE_STEPS) {
                        release(mp)
                    } else {
                        step++
                        handler.postDelayed(this, FADE_STEP_MS)
                    }
                }
            },
            APPLAUSE_PLAY_MS,
        )
    }

    private fun playIfEnabled(
        @StringRes keyRes: Int,
        @RawRes res: Int,
    ) {
        if (isEnabled(keyRes)) play(res)
    }

    private fun play(
        @RawRes res: Int,
        onComplete: (() -> Unit)? = null,
    ) {
        val mp = create(res)
        if (mp == null) {
            onComplete?.invoke()
            return
        }
        mp.setOnCompletionListener {
            release(it)
            onComplete?.invoke()
        }
        try {
            mp.start()
        } catch (e: Exception) {
            Timber.w(e, "failed to start sfx")
            release(mp)
            onComplete?.invoke()
        }
    }

    private fun create(
        @RawRes res: Int,
    ): MediaPlayer? =
        try {
            MediaPlayer.create(appContext, res)
        } catch (e: Exception) {
            Timber.w(e, "failed to create MediaPlayer for sfx")
            null
        }

    private fun isAlive(mp: MediaPlayer): Boolean =
        try {
            mp.isPlaying
            true
        } catch (e: IllegalStateException) {
            false
        }

    private fun release(mp: MediaPlayer) {
        try {
            mp.setOnCompletionListener(null)
            mp.release()
        } catch (e: Exception) {
            Timber.w(e, "failed to release MediaPlayer")
        }
    }

    /** 마스터 스위치가 켜져 있고, 해당 효과음의 개별 스위치도 켜져 있으면 true */
    private fun isEnabled(
        @StringRes keyRes: Int,
    ): Boolean {
        val prefs = appContext.sharedPrefs()
        val masterKey = appContext.getString(R.string.sound_effects_enabled_key)
        if (!prefs.getBoolean(masterKey, true)) return false
        return prefs.getBoolean(appContext.getString(keyRes), true)
    }

    companion object {
        /** 박수 소리 정상 재생 시간(ms). 이후 페이드아웃 시작. */
        private const val APPLAUSE_PLAY_MS = 5_000L

        /** 페이드아웃 단계 수 */
        private const val FADE_STEPS = 20

        /** 페이드아웃 각 단계 간격(ms). 총 페이드 시간 = FADE_STEPS * FADE_STEP_MS = 1초 */
        private const val FADE_STEP_MS = 50L
    }
}
