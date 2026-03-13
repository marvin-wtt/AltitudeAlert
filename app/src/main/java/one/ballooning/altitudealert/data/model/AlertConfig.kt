package one.ballooning.altitudealert.data.model

import kotlinx.serialization.Serializable

enum class PreferredSource { BAROMETER, GPS }

@Serializable
data class MaxAltitudeConfig(
    val enabled: Boolean = false,
    val exceedanceMarginFeet: Float = 100f,
    val minAltitudeFeet: Float = 1000f,
    val silenceDurationMinutes: Int = 5,
)

@Serializable
data class AlertConfig(
    val lowerLimitFeet: Float = 2000f,
    val upperLimitFeet: Float = 3000f,
    val qnhHpa: Float = 1013.25f,
    val useFlightLevels: Boolean = false,
    val preferredSource: PreferredSource = PreferredSource.BAROMETER,
    val approachThresholdFeet: Float = 200f,
    val maxAltitude: MaxAltitudeConfig = MaxAltitudeConfig(),
    val thresholdAlertEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
)