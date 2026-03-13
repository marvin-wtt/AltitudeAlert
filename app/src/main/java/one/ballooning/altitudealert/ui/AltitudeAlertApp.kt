package one.ballooning.altitudealert.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
fun AltitudeAlertApp(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val safeModifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)

    // Intercept actions that require Activity-level handling before forwarding to ViewModel
    val onAction: (MainAction) -> Unit = { action ->
        when (action) {
            MainAction.RequestPermissions -> onRequestPermissions()
            MainAction.OpenAppSettings -> onOpenAppSettings()
            else -> viewModel.onAction(action)
        }
    }

    when (uiState.currentScreen) {
        AppScreen.MAIN -> MainScreen(
            uiState = uiState,
            onAction = onAction,
            modifier = safeModifier,
        )
        AppScreen.ADVANCED_SETTINGS -> AdvancedSettingsScreen(
            uiState = uiState,
            onAction = viewModel::onAdvancedAction,
            modifier = safeModifier,
        )
    }
}