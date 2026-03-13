package one.ballooning.altitudealert.data.source

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import one.ballooning.altitudealert.data.model.AltitudeReading

class FusedAltitudeDataSource(private val context: Context) : AltitudeDataSource {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override val hasBarometer: Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    override val readings: Flow<AltitudeReading> =
        if (hasBarometer) combinedFlow() else gpsOnlyFlow()

    private fun combinedFlow(): Flow<AltitudeReading> =
        barometerFlow().combine(gpsFlowWithTimeout()) { baro, (altitude, accuracy) ->
            AltitudeReading(
                pressureHpa = baro,
                gpsAltitudeMetres = altitude,
                gpsVerticalAccuracyMetres = accuracy,
            )
        }

    private fun gpsOnlyFlow(): Flow<AltitudeReading> =
        gpsFlowWithTimeout().map { (altitude, accuracy) ->
            AltitudeReading(
                pressureHpa = null,
                gpsAltitudeMetres = altitude,
                gpsVerticalAccuracyMetres = accuracy,
            )
        }

    private fun gpsFlowWithTimeout(): Flow<Pair<Float?, Float?>> =
        gpsFlow().transformLatest { value ->
                emit(value)
                delay(GPS_TIMEOUT_MS)
                emit(null to null)
            }.onStart { emit(null to null) }

    private fun barometerFlow(): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)!!
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    @SuppressLint("MissingPermission")
    private fun gpsFlow(): Flow<Pair<Float?, Float?>> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location = result.lastLocation ?: return
                val altitude = if (loc.hasAltitude()) loc.altitude.toFloat() else null
                val accuracy =
                    if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else null
                trySend(altitude to accuracy)
            }
        }
        client.requestLocationUpdates(request, ContextCompat.getMainExecutor(context), callback)
        awaitClose { client.removeLocationUpdates(callback) }
    }

    companion object {
        private const val GPS_TIMEOUT_MS = 5_000L
    }
}