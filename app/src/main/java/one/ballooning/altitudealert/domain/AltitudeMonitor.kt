package one.ballooning.altitudealert.domain

import one.ballooning.altitudealert.data.model.AlertConfig
import one.ballooning.altitudealert.data.model.AltitudeReading
import one.ballooning.altitudealert.data.model.PreferredSource
import one.ballooning.altitudealert.util.AltitudeConverter.metresToFeet
import one.ballooning.altitudealert.util.AltitudeConverter.pressureToAltitudeFeet
import one.ballooning.altitudealert.util.AltitudeConverter.pressureToFlightLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.scan
import kotlin.math.max

// Pure domain class — no Android imports, no DI annotations.
// Receives its inputs as constructor parameters so it is trivially testable.
class AltitudeMonitor(
    private val readings: Flow<AltitudeReading>,
    private val config: Flow<AlertConfig>,
    private val engine: AlertEngine = AlertEngine(),
) {
    fun monitorState(): Flow<MonitorState> = combine(
        readings,
        config
    ) { reading, cfg -> reading to cfg }.scan<Pair<AltitudeReading, AlertConfig>, MonitorState?>(
            null
        ) { prev, (reading, cfg) ->
            val sourceType = resolvedSource(reading, cfg)
            val altitudeFeet = deriveAltitudeFeet(reading, sourceType, cfg)
            MonitorState(
                altitudeFeet = altitudeFeet,
                sessionMaxFeet = if (prev == null) altitudeFeet else max(
                    prev.sessionMaxFeet, altitudeFeet
                ),
                flightLevel = deriveFlightLevel(reading, cfg),
                alertResult = engine.evaluate(altitudeFeet, cfg),
                altitudeSource = sourceType,
                gpsAccuracyStatus = deriveGpsAccuracyStatus(reading, sourceType),
                gpsVerticalAccuracyFeet = reading.gpsVerticalAccuracyMetres?.let {
                    metresToFeet(
                        it
                    )
                },
            )
        }.filterNotNull()

    private fun resolvedSource(reading: AltitudeReading, cfg: AlertConfig): AltitudeSourceType =
        when {
            reading.pressureHpa == null -> AltitudeSourceType.GPS
            cfg.preferredSource == PreferredSource.GPS -> AltitudeSourceType.GPS
            else -> AltitudeSourceType.BAROMETER
        }

    private fun deriveAltitudeFeet(
        reading: AltitudeReading,
        sourceType: AltitudeSourceType,
        cfg: AlertConfig,
    ): Float = when (sourceType) {
        AltitudeSourceType.BAROMETER -> pressureToAltitudeFeet(reading.pressureHpa!!, cfg.qnhHpa)

        AltitudeSourceType.GPS -> reading.gpsAltitudeMetres?.let { metresToFeet(it) }
            ?: reading.pressureHpa?.let { pressureToAltitudeFeet(it, cfg.qnhHpa) }
            ?: error("No altitude available")
    }

    private fun deriveFlightLevel(reading: AltitudeReading, cfg: AlertConfig): Int? {
        if (!cfg.useFlightLevels) return null
        return reading.pressureHpa?.let { pressureToFlightLevel(it) }
    }

    private fun deriveGpsAccuracyStatus(
        reading: AltitudeReading,
        sourceType: AltitudeSourceType,
    ): GpsAccuracyStatus = when {
        sourceType != AltitudeSourceType.GPS -> GpsAccuracyStatus.NOT_APPLICABLE
        reading.gpsAltitudeMetres == null -> GpsAccuracyStatus.LOST
        reading.gpsVerticalAccuracyMetres == null -> GpsAccuracyStatus.GOOD
        metresToFeet(reading.gpsVerticalAccuracyMetres) > GPS_LOW_ACCURACY_THRESHOLD_FEET -> GpsAccuracyStatus.LOW

        else -> GpsAccuracyStatus.GOOD
    }

    companion object {
        private const val GPS_LOW_ACCURACY_THRESHOLD_FEET = 100f
    }
}