package one.ballooning.altitudealert.data

import kotlinx.serialization.Serializable

enum class PreferredSource { BAROMETER, GPS }

@Serializable
data class MaxAltitudeConfig(
    val enabled: Boolean = false,
    val exceedanceMarginFeet: Float = 500f,
    val minAltitudeFeet: Float = 2000f,
    val silenceDurationMinutes: Int = 60,
)

// ─── Main alert config ────────────────────────────────────────────────────────

@Serializable
data class AlertConfig(
    val lowerLimitFeet: Float = 2800f,
    val upperLimitFeet: Float = 3200f,
    val qnhHpa: Float = 1013.25f,
    val useFlightLevels: Boolean = false,
    val preferredSource: PreferredSource = PreferredSource.BAROMETER,
    val approachThresholdFeet: Float = 200f,
    val maxAltitude: MaxAltitudeConfig = MaxAltitudeConfig(),
    val vibrate: Boolean = true,
    val alarmSoundUri: String? = null,
    val alertsEnabled: Boolean = false,
)