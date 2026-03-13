package one.ballooning.altitudealert.service

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmSoundPlayer(private val scope: CoroutineScope) {

    private val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, VOLUME)
    private var crossingJob: Job? = null

    fun playThreshold() {
        scope.launch {
            repeat(3) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS.toInt())
                delay(BEEP_GAP_MS)
            }
        }
    }

    fun playMaxAltitude() {
        scope.launch {
            toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_SS, MAX_ALTITUDE_TONE_MS.toInt())
            delay(MAX_ALTITUDE_TONE_MS + BEEP_GAP_MS)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, MAX_ALTITUDE_TONE_MS.toInt())
        }
    }

    fun startCrossing() {
        if (crossingJob?.isActive == true) return
        crossingJob = scope.launch {
            while (isActive) {
                // High pair
                repeat(2) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS.toInt())
                    delay(BEEP_MS + BEEP_GAP_MS)
                }
                delay(PAIR_GAP_MS)
                // Low pair
                repeat(2) {
                    toneGen.startTone(ToneGenerator.TONE_CDMA_LOW_SS, BEEP_MS.toInt())
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
        private const val VOLUME = 100   // ToneGenerator max is 100
        private const val BEEP_MS = 160L  // duration of each beep
        private const val BEEP_GAP_MS = 80L   // silence between beeps in a pair
        private const val PAIR_GAP_MS = 350L  // silence between high and low pairs
        private const val MAX_ALTITUDE_TONE_MS = 400L  // duration of each max altitude tone
    }
}