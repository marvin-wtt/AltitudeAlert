package one.ballooning.altitudealert.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.ContextCompat

data class SystemInfo(
    val hasBarometer: Boolean,
    val fineLocationGranted: Boolean,
    val coarseLocationGranted: Boolean,
    val notificationsGranted: Boolean,
) {
    val hasForegroundLocation: Boolean get() = fineLocationGranted || coarseLocationGranted
    val canMonitor: Boolean get() = hasForegroundLocation && notificationsGranted
}

class SystemInfoRepository(private val context: Context) {

    val hasBarometer: Boolean by lazy {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    }

    fun querySystemInfo(): SystemInfo = SystemInfo(
        hasBarometer = hasBarometer,
        fineLocationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocationGranted = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
        notificationsGranted = isGranted(Manifest.permission.POST_NOTIFICATIONS),
    )

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}