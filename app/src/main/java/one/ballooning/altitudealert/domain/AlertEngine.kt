package one.ballooning.altitudealert.domain

import one.ballooning.altitudealert.data.model.AlertConfig
import kotlin.math.abs

object AlertEngine {

    fun evaluate(altitudeFeet: Float?, config: AlertConfig, alertsEnabled: Boolean): AlertResult {
        if (!alertsEnabled || altitudeFeet == null) return AlertResult(null, null)
        return AlertResult(
            lower = evaluateLimit(
                altitudeFeet,
                config.lowerLimitFeet,
                approachFromAbove = true,
                config.approachThresholdFeet
            ),
            upper = evaluateLimit(
                altitudeFeet,
                config.upperLimitFeet,
                approachFromAbove = false,
                config.approachThresholdFeet
            ),
        )
    }

    private fun evaluateLimit(
        altitudeFeet: Float,
        limitFeet: Float,
        approachFromAbove: Boolean,
        threshold: Float,
    ): LimitAlert {
        val distanceFeet = abs(altitudeFeet - limitFeet)
        val crossed =
            if (approachFromAbove) altitudeFeet <= limitFeet else altitudeFeet >= limitFeet
        val status = when {
            crossed -> AlertStatus.CROSSED
            distanceFeet <= threshold -> AlertStatus.APPROACHING
            else -> AlertStatus.CLEAR
        }
        return LimitAlert(status = status, distanceFeet = distanceFeet)
    }
}