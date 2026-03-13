package one.ballooning.altitudealert.data.source

import kotlinx.coroutines.flow.Flow
import one.ballooning.altitudealert.data.model.AltitudeReading

interface AltitudeDataSource {
    val hasBarometer: Boolean
    val readings: Flow<AltitudeReading>
}