package one.ballooning.altitudealert.notification

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import one.ballooning.altitudealert.R
import one.ballooning.altitudealert.data.model.AlertConfig
import one.ballooning.altitudealert.domain.AlertStatus
import one.ballooning.altitudealert.domain.GpsAccuracyStatus
import one.ballooning.altitudealert.domain.MonitorState

data class FormattedStatus(
    val title: String,
    val body: String,
    val bigText: String,
    @DrawableRes val iconRes: Int,
    @ColorRes val colorRes: Int,
)

object MonitorStatusFormatter {

    fun format(state: MonitorState, config: AlertConfig, alertsEnabled: Boolean): FormattedStatus {
        val overallStatus = state.alertResult.overallStatus
        return FormattedStatus(
            title = formatTitle(state, overallStatus, alertsEnabled),
            body = formatBody(state, config),
            bigText = formatBigText(state, config, alertsEnabled),
            iconRes = iconRes(overallStatus),
            colorRes = colorRes(overallStatus),
        )
    }

    // ── Title: status or alert reason — the first thing a pilot reads ─────────

    private fun formatTitle(
        state: MonitorState,
        overallStatus: AlertStatus,
        alertsEnabled: Boolean
    ): String {
        if (!alertsEnabled) return "Monitoring disabled"
        if (state.gpsAccuracyStatus == GpsAccuracyStatus.LOST) return "GPS signal lost"
        if (state.altitudeFeet == null) return "Acquiring altitude…"

        val result = state.alertResult
        val upperStatus = result.upper?.status ?: AlertStatus.CLEAR

        return when (overallStatus) {
            AlertStatus.CLEAR -> "Monitoring active"
            AlertStatus.CROSSED ->
                if (upperStatus == AlertStatus.CROSSED) "Above upper limit"
                else "Below lower limit"

            AlertStatus.APPROACHING ->
                if (upperStatus == AlertStatus.APPROACHING) "Approaching upper limit"
                else "Approaching lower limit"
        }
    }

    // ── Body: current altitude + band — the two numbers needed at a glance ────

    private fun formatBody(state: MonitorState, config: AlertConfig): String {
        val band = "%,d – %,d ft".format(
            config.lowerLimitFeet.toInt(),
            config.upperLimitFeet.toInt(),
        )
        if (state.gpsAccuracyStatus == GpsAccuracyStatus.LOST) return "Altitude unavailable · $band"
        if (state.altitudeFeet == null) return "Acquiring altitude…"
        return "$band · ${formatAltitude(state)}"
    }

    // ── BigText: expanded detail shown when no ProgressStyle is available ─────

    private fun formatBigText(
        state: MonitorState,
        config: AlertConfig,
        alertsEnabled: Boolean
    ): String = buildString {
        if (alertsEnabled) {
            append(
                "%,d – %,d ft".format(
                    config.lowerLimitFeet.toInt(),
                    config.upperLimitFeet.toInt(),
                )
            )
        }

        state.altitudeFeet?.let {
            if (isNotEmpty()) append("\n")
            append("Altitude: ${formatAltitude(state)}")
        }

        if (alertsEnabled) {
            state.alertResult.lower?.takeIf { it.status != AlertStatus.CLEAR }
                ?.let { append("\nLower limit: ${limitStatusLine(it.status, it.distanceFeet)}") }
            state.alertResult.upper?.takeIf { it.status != AlertStatus.CLEAR }
                ?.let { append("\nUpper limit: ${limitStatusLine(it.status, it.distanceFeet)}") }
        }
    }

    private fun formatAltitude(state: MonitorState): String = when {
        state.gpsAccuracyStatus == GpsAccuracyStatus.LOST -> "GPS lost"
        state.altitudeFeet == null -> "– – –"
        else -> "%,d ft".format(state.altitudeFeet.toInt())
    }

    private fun limitStatusLine(status: AlertStatus, distanceFeet: Float): String = when (status) {
        AlertStatus.CLEAR -> "%,d ft".format(distanceFeet.toInt())
        AlertStatus.APPROACHING -> "Approaching — %,d ft away".format(distanceFeet.toInt())
        AlertStatus.CROSSED -> "Exceeded"
    }

    @DrawableRes
    private fun iconRes(status: AlertStatus): Int = when (status) {
        AlertStatus.CLEAR -> R.drawable.ic_notification_clear
        AlertStatus.APPROACHING -> R.drawable.ic_notification_warning
        AlertStatus.CROSSED -> R.drawable.ic_notification_alert
    }

    @ColorRes
    private fun colorRes(status: AlertStatus): Int = when (status) {
        AlertStatus.CLEAR -> R.color.notification_clear
        AlertStatus.APPROACHING -> R.color.notification_approaching
        AlertStatus.CROSSED -> R.color.notification_crossed
    }
}