package one.ballooning.altitudealert.service

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles the two distinct alarm sounds:
 *
 * - Threshold (approach): three short beeps, played once.
 * - Crossing (limit exceeded): continuous alternating two-tone loop until [stopCrossing] is called.
 *
 * Uses [ToneGenerator] so no audio files are needed.
 */
class AlarmSoundPlayer(private val scope: CoroutineScope) {

    private val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, VOLUME)
    private var crossingJob: Job? = null

    fun playThreshold() {
        scope.launch {
            repeat(3) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
                delay(BEEP_INTERVAL_MS)
            }
        }
    }

    fun startCrossing() {
        Log.d("AlarmSoundPlayer", "startCrossing")
        if (crossingJob?.isActive == true) return
        crossingJob = scope.launch {
            while (isActive) {
                // High pair
                repeat(2) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
                    delay(BEEP_MS + BEEP_GAP_MS)
                }
                delay(PAIR_GAP_MS)
                // Low pair
                repeat(2) {
                    toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, BEEP_MS)
                    delay(BEEP_MS + BEEP_GAP_MS)
                }
                delay(PAIR_GAP_MS)
            }
        }
    }

    fun stopCrossing() {
        crossingJob?.cancel()
        crossingJob = null
        toneGen.stopTone()
    }

    fun release() {
        stopCrossing()
        toneGen.release()
    }

    companion object {
        private const val VOLUME = 100        // ToneGenerator max is 100
        private const val BEEP_MS = 150
        private const val BEEP_INTERVAL_MS = 220L   // on + gap
        private const val BEEP_GAP_MS  = 80L // silence between beeps in a pair
        private const val PAIR_GAP_MS  = 350L  // silence between high and low pairs
    }
}