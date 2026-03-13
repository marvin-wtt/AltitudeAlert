package one.ballooning.altitudealert.data.model

import android.os.SystemClock

data class AltitudeReading(
    val pressureHpa: Float?,
    val gpsAltitudeMetres: Float?,
    val gpsVerticalAccuracyMetres: Float?,
    val timestampMs: Long = SystemClock.elapsedRealtime(),
)