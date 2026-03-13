package one.ballooning.altitudealert

import android.app.Application
import one.ballooning.altitudealert.data.repository.ConfigRepository
import one.ballooning.altitudealert.data.repository.DataStoreConfigRepository
import one.ballooning.altitudealert.data.repository.SystemInfoRepository
import one.ballooning.altitudealert.data.source.AltitudeDataSource
import one.ballooning.altitudealert.data.source.FusedAltitudeDataSource

/**
 * Task list
 * TODO Find a way to close the app properly
 * FIXME Disable start button until service is ready with data
 * FIXME No alerts for altitude band
 * TODO Validate Barometer against GPS
 * TODO Alert when GPS is list
 * TODO Merge Altitude and flight levels
 * TODO Set QHN to standard for flight levels
 * TODO Order settings page
 * TODO Update max altitude style
 * TODO Show in UI when altitude max alerts are silenced
 */

class AltitudeAlertApplication : Application() {

    val configRepository: ConfigRepository by lazy {
        DataStoreConfigRepository(this)
    }

    val altitudeDataSource: AltitudeDataSource by lazy {
        FusedAltitudeDataSource(this)
    }

    val systemInfoRepository: SystemInfoRepository by lazy {
        SystemInfoRepository(this)
    }
}