package one.ballooning.altitudealert.monitor

import one.ballooning.altitudealert.data.AlertConfig
import kotlin.math.abs

class AlertEngine {

    fun evaluate(altitudeFeet: Float, config: AlertConfig): AlertResult {
        if (!config.alertsEnabled) return AlertResult(lower = null, upper = null)

        return AlertResult(
            lower = evaluateLimit(
                altitudeFeet      = altitudeFeet,
                limitFeet         = config.lowerLimitFeet,
                approachFromAbove = true,
                threshold         = config.approachThresholdFeet,
            ),
            upper = evaluateLimit(
                altitudeFeet      = altitudeFeet,
                limitFeet         = config.upperLimitFeet,
                approachFromAbove = false,
                threshold         = config.approachThresholdFeet,
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
        val crossed = if (approachFromAbove) altitudeFeet <= limitFeet
        else                   altitudeFeet >= limitFeet
        val status = when {
            crossed                   -> AlertStatus.CROSSED
            distanceFeet <= threshold -> AlertStatus.APPROACHING
            else                      -> AlertStatus.CLEAR
        }
        return LimitAlert(status = status, distanceFeet = distanceFeet)
    }
}