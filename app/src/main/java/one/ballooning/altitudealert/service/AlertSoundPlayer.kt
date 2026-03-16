package one.ballooning.altitudealert.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import one.ballooning.altitudealert.R

class AlarmSoundPlayer(context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)   // one looping alarm + threshold one-shot may briefly overlap
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()

    private val soundThreshold = soundPool.load(context, R.raw.alarm_threshold, 1)
    private val soundCrossing = soundPool.load(context, R.raw.alarm_limit, 1)
    private val soundMaxAltitude = soundPool.load(context, R.raw.alarm_max_altitude, 1)

    private var crossingStreamId = 0
    private var maxAltitudeStreamId = 0

    private var crossingDesired = false
    private var maxAltitudeDesired = false

    // ── One-shot ──────────────────────────────────────────────────────────────

    fun playThreshold() {
        soundPool.play(soundThreshold, 1f, 1f, 1, 0, 1f)
    }

    // ── Intent setters ────────────────────────────────────────────────────────

    fun startCrossing() {
        crossingDesired = true
        sync()
    }

    fun stopCrossing() {
        crossingDesired = false
        sync()
    }

    fun startMaxAltitude() {
        maxAltitudeDesired = true
        sync()
    }

    fun stopMaxAltitude() {
        maxAltitudeDesired = false
        sync()
    }

    // ── Priority sync ─────────────────────────────────────────────────────────

    private fun sync() {
        when {
            crossingDesired -> {
                stopMaxAltitudeStream()
                if (crossingStreamId == 0) {
                    crossingStreamId = soundPool.play(soundCrossing, 1f, 1f, 1, -1, 1f)
                }
            }

            maxAltitudeDesired -> {
                stopCrossingStream()
                if (maxAltitudeStreamId == 0) {
                    maxAltitudeStreamId = soundPool.play(soundMaxAltitude, 1f, 1f, 1, -1, 1f)
                }
            }

            else -> {
                stopCrossingStream()
                stopMaxAltitudeStream()
            }
        }
    }

    private fun stopCrossingStream() {
        if (crossingStreamId != 0) {
            soundPool.stop(crossingStreamId)
            crossingStreamId = 0
        }
    }

    private fun stopMaxAltitudeStream() {
        if (maxAltitudeStreamId != 0) {
            soundPool.stop(maxAltitudeStreamId)
            maxAltitudeStreamId = 0
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun release() {
        crossingDesired = false
        maxAltitudeDesired = false
        soundPool.release()
    }
}