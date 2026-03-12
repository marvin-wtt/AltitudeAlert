package one.ballooning.altitudealert.util

import android.hardware.SensorManager

object AltitudeConverter {

    private const val METRES_TO_FEET = 3.28084f
    private const val FEET_TO_METRES = 1f / METRES_TO_FEET
    private const val FL_DIVISOR = 100f

    // ─── Barometric altitude ──────────────────────────────────────────────────

    /** QNH-referenced altitude in feet from raw barometer pressure. */
    fun pressureToAltitudeFeet(pressureHpa: Float, qnhHpa: Float): Float =
        SensorManager.getAltitude(qnhHpa, pressureHpa) * METRES_TO_FEET

    /** Flight level from raw barometer pressure (ISA reference, independent of QNH). */
    fun pressureToFlightLevel(pressureHpa: Float): Int =
        (SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa)
                * METRES_TO_FEET / FL_DIVISOR).toInt()

    // ─── Unit conversion ──────────────────────────────────────────────────────

    fun metresToFeet(metres: Float): Float = metres * METRES_TO_FEET
    fun feetToMetres(feet: Float): Float = feet * FEET_TO_METRES

    // ─── Flight level helpers ─────────────────────────────────────────────────

    /** Nominal pressure altitude in feet implied by a flight level (e.g. FL65 → 6500 ft). */
    fun flightLevelToFeet(fl: Int): Float = fl * FL_DIVISOR

    /** Display string, e.g. 65 → "FL065". */
    fun formatFlightLevel(fl: Int): String = "FL%03d".format(fl)
}