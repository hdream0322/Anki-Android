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
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import timber.log.Timber

/**
 * 학습 중 재생되는 효과음(SFX) 플레이어.
 *
 * 짧은 효과음(정답·오답·낙담)은 [SoundPool]로 재생한다. 음원을 미리 메모리에
 * 디코딩해 두므로, 버튼을 누르는 순간 `prepare()`나 오디오 트랙 준비로 인한
 * 지연 없이 즉시 재생된다. 매번 [MediaPlayer]를 새로 만들던 방식에서 가끔
 * 소리 앞부분이 잘리던(씹히던) 문제를 없애기 위함이다.
 *
 * 박수 소리는 길이가 길고(약 26초) 페이드아웃이 필요하므로 [MediaPlayer]로 재생한다.
 * [applicationContext][Context.getApplicationContext]를 사용하므로 학습 화면이
 * 닫히는 시점(예: 덱 완료 시 박수 소리)에도 재생이 끊기지 않는다.
 *
 * [SoundPool]은 프로세스 전역에서 하나만 만들어 공유한다. 그래야 학습 화면을
 * 여닫을 때마다 풀을 만들고 해제하느라 생기는 누수·지연이 없고, 덱 완료로 학습
 * 화면이 곧바로 파괴되어도 마지막 정답 효과음이 잘리지 않는다.
 *
 * 재생 여부는 설정의 마스터 스위치([R.string.sound_effects_enabled_key])와
 * 각 효과음별 개별 스위치로 제어된다. 둘 중 하나라도 꺼져 있으면 재생되지 않는다.
 */
class SoundEffectPlayer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    /** 박수 소리가 실제로 재생을 시작한 횟수 (테스트에서 재생 순서를 검증하기 위함). */
    @VisibleForTesting
    internal var applauseStartCount = 0
        private set

    /** 정답(correct) 효과음이 현재 재생 중인지 여부. 박수 소리를 이어 재생할지 판단에 사용. */
    private var correctPlaying = false

    /** 정답 효과음이 끝날 때까지 박수 재생을 보류했는지 여부. */
    private var applausePending = false

    /** 정답 효과음 재생이 끝났다고 간주하는 시점에 호출되는 작업. */
    private val correctFinishedRunnable = Runnable { onCorrectFinished() }

    /**
     * 좋음(Good) 버튼: 정답 효과음.
     *
     * SoundPool에는 재생 완료 콜백이 없으므로, 음원 길이만큼 뒤에 [onCorrectFinished]를
     * 호출하도록 예약해 박수 소리와의 순서를 맞춘다.
     */
    fun playCorrect() {
        if (!isEnabled(R.string.sound_effect_correct_key)) return
        if (playShortSfx(appContext, R.raw.sfx_correct) == 0) return
        correctPlaying = true
        handler.removeCallbacks(correctFinishedRunnable)
        handler.postDelayed(correctFinishedRunnable, CORRECT_SFX_DURATION_MS)
    }

    /** 다시(Again) 버튼: 오답 효과음 */
    fun playError() {
        if (isEnabled(R.string.sound_effect_error_key)) playShortSfx(appContext, R.raw.sfx_error)
    }

    /** leech 카드 또는 다시/어려움 5회 연속 누적 시: 낙담 효과음 */
    fun playAwwMan() {
        if (isEnabled(R.string.sound_effect_aww_man_key)) playShortSfx(appContext, R.raw.sfx_aww_man)
    }

    /**
     * 오답(error) 효과음과 낙담(aww-man) 효과음을 동시에 시작한다.
     * 5번째 '다시'를 누르는 순간처럼 두 효과음이 함께 발생하는 경우에 사용한다.
     * 각 효과음은 SoundPool의 독립 스트림으로 재생되며, 개별 설정에 따라 생략될 수 있다.
     */
    fun playErrorAndAwwMan() {
        if (isEnabled(R.string.sound_effect_error_key)) playShortSfx(appContext, R.raw.sfx_error)
        if (isEnabled(R.string.sound_effect_aww_man_key)) playShortSfx(appContext, R.raw.sfx_aww_man)
    }

    /**
     * 덱 학습 완료 시 박수 소리. 음원은 약 26초이므로 약 3초 재생 후
     * 디졸브(페이드아웃)로 자연스럽게 소리를 줄여 멈춘다.
     *
     * 마지막 카드를 정답 처리하면 정답 효과음과 박수가 거의 동시에 발생한다.
     * 두 소리가 겹쳐 정답음이 잘리지 않도록, 정답 효과음이 재생 중이면
     * 그 재생이 끝난 뒤에 박수를 시작한다.
     */
    fun playApplause() {
        if (!isEnabled(R.string.sound_effect_applause_key)) return
        if (correctPlaying) {
            applausePending = true
            return
        }
        startApplause()
    }

    /** 정답 효과음 재생이 끝났을 때 호출. 보류된 박수 소리가 있으면 이어서 재생한다. */
    private fun onCorrectFinished() {
        correctPlaying = false
        if (applausePending) {
            applausePending = false
            startApplause()
        }
    }

    private fun startApplause() {
        applauseStartCount++
        val mp = createMediaPlayer(R.raw.sfx_applause) ?: return
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

    private fun createMediaPlayer(
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
        /**
         * 정답 효과음(`sfx_correct`, 약 1.36초)이 끝났다고 보는 시간(ms).
         * 이 시간 뒤에 보류된 박수 소리를 시작한다. 음원을 교체하면 함께 조정한다.
         */
        private const val CORRECT_SFX_DURATION_MS = 1_400L

        /** 박수 소리 정상 재생 시간(ms). 이후 페이드아웃 시작. */
        private const val APPLAUSE_PLAY_MS = 3_000L

        /** 페이드아웃 단계 수 */
        private const val FADE_STEPS = 20

        /** 페이드아웃 각 단계 간격(ms). 총 페이드 시간 = FADE_STEPS * FADE_STEP_MS = 1초 */
        private const val FADE_STEP_MS = 50L

        /** SoundPool 동시 재생 스트림 수. 오답+낙담 동시 재생 등을 고려한 여유분. */
        private const val MAX_STREAMS = 4

        /** SoundPool로 미리 로드해 둘 짧은 효과음 음원들. */
        private val SHORT_SFX = intArrayOf(R.raw.sfx_correct, R.raw.sfx_error, R.raw.sfx_aww_man)

        @Volatile
        private var soundPool: SoundPool? = null

        /** 음원 리소스 id → SoundPool 샘플 id */
        private val sampleIds = mutableMapOf<Int, Int>()

        private fun soundPool(appContext: Context): SoundPool {
            soundPool?.let { return it }
            return synchronized(this) {
                soundPool ?: SoundPool
                    .Builder()
                    .setMaxStreams(MAX_STREAMS)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    ).build()
                    .also { pool ->
                        for (res in SHORT_SFX) {
                            sampleIds[res] = pool.load(appContext, res, 1)
                        }
                        soundPool = pool
                    }
            }
        }

        /**
         * 공유 SoundPool로 짧은 효과음을 재생한다.
         * @return 재생 스트림 id. 재생에 실패하면 0.
         */
        private fun playShortSfx(
            appContext: Context,
            @RawRes res: Int,
        ): Int {
            val pool = soundPool(appContext)
            val sampleId = sampleIds[res] ?: return 0
            return try {
                pool.play(sampleId, 1f, 1f, 1, 0, 1f)
            } catch (e: Exception) {
                Timber.w(e, "failed to play sfx")
                0
            }
        }

        /** 테스트 간 공유 SoundPool 상태를 초기화한다. */
        @VisibleForTesting
        internal fun resetSharedSoundPoolForTest() {
            synchronized(this) {
                soundPool?.release()
                soundPool = null
                sampleIds.clear()
            }
        }

        /** 테스트에서 SoundPool 재생 여부를 검증하기 위한 접근자. */
        @VisibleForTesting
        internal fun soundPoolForTest(appContext: Context): SoundPool = soundPool(appContext)
    }
}
