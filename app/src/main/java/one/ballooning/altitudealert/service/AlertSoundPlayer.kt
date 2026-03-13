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
    private var maxAltitudeJob: Job? = null

    // ── One-shot ──────────────────────────────────────────────────────────────

    fun playThreshold() {
        scope.launch {
            repeat(3) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS.toInt())
                delay(BEEP_MS + BEEP_GAP_MS)
            }
        }
    }

    // ── Crossing alarm (band limit exceeded) ──────────────────────────────────
    fun startCrossing() {
        if (crossingJob?.isActive == true) return
        if (maxAltitudeJob?.isActive == true) return   // no parallel alarms
        crossingJob = scope.launch {
            while (isActive) {
                repeat(2) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS.toInt())
                    delay(BEEP_MS + BEEP_GAP_MS)
                }
                delay(PAIR_GAP_MS)
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

    val isCrossingActive: Boolean get() = crossingJob?.isActive == true

    // ── Max altitude alarm ────────────────────────────────────────────────────

    fun startMaxAltitude() {
        if (maxAltitudeJob?.isActive == true) return
        if (crossingJob?.isActive == true) return      // no parallel alarms
        maxAltitudeJob = scope.launch {
            while (isActive) {
                repeat(3) {
                    toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_SS, BEEP_MS.toInt())
                    delay(BEEP_MS + BEEP_GAP_MS)
                }
                delay(MAX_ALTITUDE_GAP_MS)
            }
        }
    }
    fun stopMaxAltitude() {
        maxAltitudeJob?.cancel()
        maxAltitudeJob = null
        toneGen.stopTone()
    }

    val isMaxAltitudeActive: Boolean get() = maxAltitudeJob?.isActive == true

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun release() {
        stopCrossing()
        stopMaxAltitude()
        toneGen.release()
    }

    companion object {
        private const val VOLUME             = 100    // ToneGenerator max
        private const val BEEP_MS            = 160L   // duration of each beep
        private const val BEEP_GAP_MS        = 80L    // gap between beeps in a pair
        private const val PAIR_GAP_MS        = 350L   // gap between pairs (crossing)
        private const val MAX_ALTITUDE_GAP_MS = 700L  // pause between max altitude triple-beep cycles
    }
}