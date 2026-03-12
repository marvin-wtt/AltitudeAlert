package one.ballooning.altitudealert.monitor

// ─── Alert result ─────────────────────────────────────────────────────────────

enum class AlertStatus { CLEAR, APPROACHING, CROSSED }

data class LimitAlert(
    val status: AlertStatus,
    val distanceFeet: Float,
)

data class AlertResult(
    val lower: LimitAlert?,
    val upper: LimitAlert?,
) {
    val overallStatus: AlertStatus
        get() = listOfNotNull(lower, upper)
            .maxOfOrNull { it.status.ordinal }
            ?.let { AlertStatus.entries[it] }
            ?: AlertStatus.CLEAR
}

// ─── GPS accuracy ─────────────────────────────────────────────────────────────

enum class GpsAccuracyStatus {
    NOT_APPLICABLE,
    GOOD,
    LOW,
    LOST,
}

// ─── Source ───────────────────────────────────────────────────────────────────

enum class AltitudeSourceType { BAROMETER, GPS }

// ─── Monitor state ────────────────────────────────────────────────────────────

data class MonitorState(
    val altitudeFeet: Float,
    val sessionMaxFeet: Float,
    val flightLevel: Int?,
    val alertResult: AlertResult,
    val altitudeSource: AltitudeSourceType,
    val gpsAccuracyStatus: GpsAccuracyStatus,
    val gpsVerticalAccuracyFeet: Float?,
    val configuredLowerLimitFeet: Float,
    val configuredUpperLimitFeet: Float,
    val alertsEnabled: Boolean,
)