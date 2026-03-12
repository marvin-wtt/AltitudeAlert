package one.ballooning.altitudealert.data

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

data class AltitudeReading(
    val pressureHpa: Float?,
    val gpsAltitudeMetres: Float?,
    val gpsVerticalAccuracyMetres: Float?,  // null if unavailable or no fix
    val timestampMs: Long = SystemClock.elapsedRealtime(),
)

fun altitudeFlow(context: Context): Flow<AltitudeReading> =
    if (hasBaro(context)) combinedFlow(context) else gpsOnlyFlow(context)

fun hasBaro(context: Context): Boolean {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    return sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
}

// ─── Combined baro + GPS ──────────────────────────────────────────────────────

private fun combinedFlow(context: Context): Flow<AltitudeReading> =
    barometerFlow(context).combine(gpsFlow(context)) { baro, gps ->
        AltitudeReading(
            pressureHpa                = baro,
            gpsAltitudeMetres          = gps.first,
            gpsVerticalAccuracyMetres  = gps.second,
        )
    }

// ─── GPS-only (no barometer) ──────────────────────────────────────────────────

private fun gpsOnlyFlow(context: Context): Flow<AltitudeReading> =
    gpsFlow(context).map { (altitude, accuracy) ->
        AltitudeReading(
            pressureHpa               = null,
            gpsAltitudeMetres         = altitude,
            gpsVerticalAccuracyMetres = accuracy,
        )
    }

// ─── Raw barometer → hPa ─────────────────────────────────────────────────────

private fun barometerFlow(context: Context): Flow<Float> = callbackFlow {
    val sm     = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)!!

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            trySend(event.values[0])
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    awaitClose {
        sm.unregisterListener(listener)
    }
}.conflate()

// ─── Raw GPS → (altitudeMetres, verticalAccuracyMetres?) ─────────────────────

@SuppressLint("MissingPermission")
private fun gpsFlow(context: Context): Flow<Pair<Float?, Float?>> = callbackFlow {
    val client  = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
        .setMinUpdateIntervalMillis(1_000L)
        .build()

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location = result.lastLocation ?: return
            val altitude = if (loc.hasAltitude()) loc.altitude.toFloat() else null
            val accuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasVerticalAccuracy())
                loc.verticalAccuracyMeters else null
            trySend(altitude to accuracy)
        }
    }

    client.requestLocationUpdates(request, ContextCompat.getMainExecutor(context), callback)

    awaitClose {
        client.removeLocationUpdates(callback)
    }
}.conflate()