package one.ballooning.altitudealert.util

import android.hardware.SensorManager

object AltitudeConverter {

    private const val METRES_TO_FEET = 3.28084f

    /** QNH-referenced altitude in feet from raw barometer pressure. */
    fun pressureToAltitudeFeet(pressureHpa: Float, qnhHpa: Float): Float =
        SensorManager.getAltitude(qnhHpa, pressureHpa) * METRES_TO_FEET

    /** Flight level from raw barometer pressure (ISA reference, independent of QNH). */
    fun pressureToFlightLevel(pressureHpa: Float): Int =
        (SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa)
                * METRES_TO_FEET / 100f).toInt()

    fun metresToFeet(metres: Float): Float = metres * METRES_TO_FEET
}