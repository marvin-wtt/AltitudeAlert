package one.ballooning.altitudealert.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import one.ballooning.altitudealert.AltitudeAlertApplication
import one.ballooning.altitudealert.service.MonitorService
import one.ballooning.altitudealert.ui.theme.AltitudeAlertTheme

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel> {
        MainViewModel.factory(application as AltitudeAlertApplication)
    }

    private var serviceBound = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshSystemInfo()
        if (results.values.any { !it }) viewModel.markPermissionsDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.refreshSystemInfo()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.canMonitor }
                    .distinctUntilChanged()
                    .collect { canMonitor ->
                        if (canMonitor) startAndBindService() else unbindAndStopService()
                    }
            }
        }

        setContent {
            AltitudeAlertTheme {
                AltitudeAlertApp(
                    viewModel            = viewModel,
                    onRequestPermissions = ::requestRequiredPermissions,
                    onOpenAppSettings    = ::openAppSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemInfo()
        if (viewModel.uiState.value.canMonitor && !serviceBound) {
            bindService(
                Intent(this, MonitorService::class.java),
                viewModel.serviceConnection,
                BIND_AUTO_CREATE,
            ).also { serviceBound = it }
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(viewModel.serviceConnection)
            serviceBound = false
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, MonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        if (!serviceBound) {
            bindService(intent, viewModel.serviceConnection, BIND_AUTO_CREATE)
                .also { serviceBound = it }
        }
    }

    private fun unbindAndStopService() {
        if (serviceBound) {
            unbindService(viewModel.serviceConnection)
            serviceBound = false
        }
        stopService(Intent(this, MonitorService::class.java))
    }

    private fun requestRequiredPermissions() {
        val needed = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filterNot { checkSelfPermission(this, it) == PERMISSION_GRANTED }

        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        else viewModel.refreshSystemInfo()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        )
    }
}