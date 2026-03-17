package one.ballooning.altitudealert.data.model

data class AltitudeReading(
    val pressureHpa: Float?,
    val gpsAltitudeMetres: Float?,
    val gpsVerticalAccuracyMetres: Float?,
)