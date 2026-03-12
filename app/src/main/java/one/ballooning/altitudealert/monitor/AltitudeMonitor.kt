package one.ballooning.altitudealert.monitor

import one.ballooning.altitudealert.data.AlertConfig
import one.ballooning.altitudealert.data.AltitudeReading
import one.ballooning.altitudealert.data.AppSettings
import one.ballooning.altitudealert.data.PreferredSource
import one.ballooning.altitudealert.util.AltitudeConverter.metresToFeet
import one.ballooning.altitudealert.util.AltitudeConverter.pressureToAltitudeFeet
import one.ballooning.altitudealert.util.AltitudeConverter.pressureToFlightLevel
import kotlinx.coroutines.flow.*
import kotlin.math.max

class AltitudeMonitor(
    private val readings: Flow<AltitudeReading>,
    private val settings: AppSettings,
    private val engine: AlertEngine = AlertEngine(),
) {
    fun monitorState(): Flow<MonitorState> =
        combine(readings, settings.configFlow) { reading, config -> reading to config }
            .scan<Pair<AltitudeReading, AlertConfig>, MonitorState?>(null) { prev, (reading, config) ->
                val sourceType = resolvedSource(reading, config)
                val altitudeFeet = deriveAltitudeFeet(reading, sourceType, config)
                val sessionMaxFeet = if (prev == null) altitudeFeet
                else max(prev.sessionMaxFeet, altitudeFeet)
                MonitorState(
                    altitudeFeet = altitudeFeet,
                    sessionMaxFeet = sessionMaxFeet,
                    flightLevel = deriveFlightLevel(reading, config),
                    alertResult = engine.evaluate(altitudeFeet, config),
                    altitudeSource = sourceType,
                    gpsAccuracyStatus = deriveGpsAccuracyStatus(reading, sourceType),
                    gpsVerticalAccuracyFeet = reading.gpsVerticalAccuracyMetres?.let {
                        metresToFeet(
                            it
                        )
                    },
                    configuredLowerLimitFeet = config.lowerLimitFeet,
                    configuredUpperLimitFeet = config.upperLimitFeet,
                    alertsEnabled = config.alertsEnabled,
                )
            }
            .filterNotNull()

    // ─── Source resolution ────────────────────────────────────────────────────

    private fun resolvedSource(reading: AltitudeReading, config: AlertConfig): AltitudeSourceType =
        when {
            reading.pressureHpa == null -> AltitudeSourceType.GPS
            config.preferredSource == PreferredSource.GPS -> AltitudeSourceType.GPS
            else -> AltitudeSourceType.BAROMETER
        }

    // ─── Altitude derivation ──────────────────────────────────────────────────

    private fun deriveAltitudeFeet(
        reading: AltitudeReading,
        sourceType: AltitudeSourceType,
        config: AlertConfig,
    ): Float = when (sourceType) {
        AltitudeSourceType.BAROMETER ->
            pressureToAltitudeFeet(reading.pressureHpa!!, config.qnhHpa)

        AltitudeSourceType.GPS ->
            reading.gpsAltitudeMetres?.let { metresToFeet(it) }
                ?: reading.pressureHpa?.let { pressureToAltitudeFeet(it, config.qnhHpa) }
                ?: error("No altitude available")
    }

    private fun deriveFlightLevel(reading: AltitudeReading, config: AlertConfig): Int? {
        if (!config.useFlightLevels) return null
        return reading.pressureHpa?.let { pressureToFlightLevel(it) }
    }

    // ─── GPS accuracy ─────────────────────────────────────────────────────────

    private fun deriveGpsAccuracyStatus(
        reading: AltitudeReading,
        sourceType: AltitudeSourceType,
    ): GpsAccuracyStatus = when {
        sourceType != AltitudeSourceType.GPS -> GpsAccuracyStatus.NOT_APPLICABLE
        reading.gpsAltitudeMetres == null -> GpsAccuracyStatus.LOST
        reading.gpsVerticalAccuracyMetres == null -> GpsAccuracyStatus.GOOD
        metresToFeet(reading.gpsVerticalAccuracyMetres)
                > GPS_LOW_ACCURACY_THRESHOLD_FEET -> GpsAccuracyStatus.LOW

        else -> GpsAccuracyStatus.GOOD
    }

    companion object {
        private const val GPS_LOW_ACCURACY_THRESHOLD_FEET = 100f
    }
}