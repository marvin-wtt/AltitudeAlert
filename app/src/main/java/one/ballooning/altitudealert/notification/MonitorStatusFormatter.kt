package one.ballooning.altitudealert.notification

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import one.ballooning.altitudealert.R
import one.ballooning.altitudealert.data.model.AlertConfig
import one.ballooning.altitudealert.domain.AlertStatus
import one.ballooning.altitudealert.domain.AltitudeSourceType
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

    fun format(state: MonitorState, config: AlertConfig): FormattedStatus {
        val overallStatus = state.alertResult.overallStatus
        return FormattedStatus(
            title = formatTitle(config),
            body = formatBody(state, overallStatus),
            bigText = formatBigText(state),
            iconRes = iconRes(overallStatus),
            colorRes = colorRes(overallStatus),
        )
    }

    private fun formatTitle(config: AlertConfig): String =
        "Band %,d – %,d ft".format(
            config.lowerLimitFeet.toInt(),
            config.upperLimitFeet.toInt(),
        )

    private fun formatBody(state: MonitorState, overallStatus: AlertStatus): String {
        val alt = formatAltitude(state)
        return when (overallStatus) {
            AlertStatus.CLEAR -> alt
            AlertStatus.APPROACHING -> "Approaching limit · $alt"
            AlertStatus.CROSSED -> "⚠ Limit crossed · $alt"
        }
    }

    private fun formatBigText(state: MonitorState): String = buildString {
        append("Altitude: ${formatAltitude(state)}")

        state.alertResult.lower
            ?.takeIf { it.status != AlertStatus.CLEAR }
            ?.let { append("\nLower: ${limitStatusLine(it.status, it.distanceFeet)}") }

        state.alertResult.upper
            ?.takeIf { it.status != AlertStatus.CLEAR }
            ?.let { append("\nUpper: ${limitStatusLine(it.status, it.distanceFeet)}") }

        append("\nSession max: %,d ft".format(state.sessionMaxFeet.toInt()))

        formatSourceNote(state).takeIf { it.isNotEmpty() }?.let { append("\n$it") }
    }

    private fun formatAltitude(state: MonitorState): String =
        if (state.flightLevel != null) "FL%03d".format(state.flightLevel)
        else "%,d ft".format(state.altitudeFeet.toInt())

    private fun limitStatusLine(status: AlertStatus, distanceFeet: Float): String =
        when (status) {
            AlertStatus.CLEAR -> "%,d ft".format(distanceFeet.toInt())
            AlertStatus.APPROACHING -> "Approaching — %,d ft away".format(distanceFeet.toInt())
            AlertStatus.CROSSED -> "⚠ Crossed"
        }

    private fun formatSourceNote(state: MonitorState): String {
        if (state.altitudeSource != AltitudeSourceType.GPS) return ""
        val acc = state.gpsVerticalAccuracyFeet?.let { " ±${it.toInt()} ft" } ?: ""
        return when (state.gpsAccuracyStatus) {
            GpsAccuracyStatus.LOST -> "GPS signal lost"
            GpsAccuracyStatus.LOW -> "GPS low accuracy$acc"
            else -> "Source: GPS$acc"
        }
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