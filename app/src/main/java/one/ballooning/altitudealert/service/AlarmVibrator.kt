package one.ballooning.altitudealert.service

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.content.getSystemService

class AlarmVibrator(context: Context) {

    private val vibrator = context.getSystemService<VibratorManager>()!!.defaultVibrator

    private var crossingDesired = false
    private var maxAltitudeDesired = false

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

    fun playApproaching() {
        vibrate(VibrationEffect.createWaveform(PATTERN_SHORT, -1))
    }

    fun cancel() {
        crossingDesired = false
        maxAltitudeDesired = false
        vibrator.cancel()
    }

    private fun sync() {
        if (crossingDesired || maxAltitudeDesired) {
            vibrate(VibrationEffect.createWaveform(PATTERN_CYCLE, 0))
        } else {
            vibrator.cancel()
        }
    }

    private fun vibrate(effect: VibrationEffect) {
        vibrator.vibrate(
            effect,
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    companion object {
        private val PATTERN_SHORT = longArrayOf(0, 150, 100, 150)
        private val PATTERN_CYCLE = longArrayOf(0, 400, 200, 400, 200, 400)
    }
}