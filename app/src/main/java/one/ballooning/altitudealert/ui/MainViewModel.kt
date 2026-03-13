package one.ballooning.altitudealert.ui

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.ballooning.altitudealert.AltitudeAlertApplication
import one.ballooning.altitudealert.data.model.AlertConfig
import one.ballooning.altitudealert.data.model.MaxAltitudeConfig
import one.ballooning.altitudealert.data.model.PreferredSource
import one.ballooning.altitudealert.data.repository.ConfigRepository
import one.ballooning.altitudealert.data.repository.SystemInfo
import one.ballooning.altitudealert.data.repository.SystemInfoRepository
import one.ballooning.altitudealert.domain.AlertResult
import one.ballooning.altitudealert.domain.AltitudeSourceType
import one.ballooning.altitudealert.domain.GpsAccuracyStatus
import one.ballooning.altitudealert.domain.MonitorState
import one.ballooning.altitudealert.service.MonitorService

// ─── Navigation ───────────────────────────────────────────────────────────────

enum class AppScreen { MAIN, ADVANCED_SETTINGS }
// ─── Readiness ────────────────────────────────────────────────────────────────

enum class MonitorReadiness { READY, MISSING_PERMISSIONS, MISSING_ALTITUDE_SOURCE }

// ─── Sub-states ───────────────────────────────────────────────────────────────

data class LiveAltitudeStatus(
    val altitudeFeet: Float? = null,
    val flightLevel: Int? = null,
    val alertResult: AlertResult? = null,
    val altitudeSource: AltitudeSourceType? = null,
    val gpsAccuracyStatus: GpsAccuracyStatus? = null,
    val gpsVerticalAccuracyFeet: Float? = null,
    val sessionMaxFeet: Float? = null,
)

// ─── UI state ─────────────────────────────────────────────────────────────────

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.MAIN,
    val alertsEnabled: Boolean = false,

    val hasBarometer: Boolean = false,
    val fineLocationGranted: Boolean = false,
    val coarseLocationGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val permissionsPreviouslyDenied: Boolean = false,

    val preferredSource: PreferredSource = PreferredSource.BAROMETER,

    val bandLowerAltitudeFeet: String = "2800",
    val bandUpperAltitudeFeet: String = "3200",
    val qnhHpa: String = "1013",

    val approachThresholdFeet: String = "200",

    val thresholdAlertEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val crossingAlarmMuted: Boolean = false,

    val warnOnLowAccuracy: Boolean = true,

    val maxAltitudeEnabled: Boolean = false,
    val maxAltitudeThresholdFeet: String = "50",
    val maxAltitudeMinAltitudeFeet: String = "500",
    val maxAltitudeSilenceMinutes: String = "5",

    val liveStatus: LiveAltitudeStatus = LiveAltitudeStatus(),
) {
    val hasForegroundLocation: Boolean get() = fineLocationGranted || coarseLocationGranted
    val canMonitor: Boolean get() = hasForegroundLocation && notificationsGranted
    val altitudeSourceAvailable: Boolean get() = hasBarometer || hasForegroundLocation

    val monitorReadiness: MonitorReadiness
        get() = when {
            !canMonitor -> MonitorReadiness.MISSING_PERMISSIONS
            !altitudeSourceAvailable -> MonitorReadiness.MISSING_ALTITUDE_SOURCE
            else -> MonitorReadiness.READY
        }

    val showQnh: Boolean get() = preferredSource == PreferredSource.BAROMETER || !hasBarometer

    val qnhValidation: ValidationResult
        get() = if (showQnh) Validators.qnh(qnhHpa) else ValidationResult.Valid
    val bandLowerAltitudeValidation: ValidationResult
        get() = Validators.bandLower(bandLowerAltitudeFeet, bandUpperAltitudeFeet)
    val bandUpperAltitudeValidation: ValidationResult
        get() = Validators.bandUpper(bandUpperAltitudeFeet, bandLowerAltitudeFeet)
    val approachThresholdValidation: ValidationResult
        get() = Validators.approachThreshold(approachThresholdFeet)
    val maxAltitudeThresholdValidation: ValidationResult
        get() = if (maxAltitudeEnabled) Validators.maxAltitudeThreshold(maxAltitudeThresholdFeet)
        else ValidationResult.Valid
    val maxAltitudeMinAltitudeValidation: ValidationResult
        get() = if (maxAltitudeEnabled) Validators.maxAltitudeMinAltitude(maxAltitudeMinAltitudeFeet)
        else ValidationResult.Valid
    val maxAltitudeSilenceValidation: ValidationResult
        get() = if (maxAltitudeEnabled) Validators.silenceMinutes(maxAltitudeSilenceMinutes)
        else ValidationResult.Valid
    val isConfigValid: Boolean
        get() = listOf(
            qnhValidation,
            bandLowerAltitudeValidation,
            bandUpperAltitudeValidation,
            approachThresholdValidation,
            maxAltitudeThresholdValidation,
            maxAltitudeMinAltitudeValidation,
            maxAltitudeSilenceValidation,
        ).all { it.isValid }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MainViewModel(
    private val configRepository: ConfigRepository,
    private val systemInfoRepository: SystemInfoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        // Pre-populate UI from persisted config so the user never sees blank fields.
        viewModelScope.launch {
            configRepository.configFlow.collect { config ->
                _uiState.update { applyConfigToUiState(it, config) }
            }
        }
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    companion object {
        fun factory(app: AltitudeAlertApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(app.configRepository, app.systemInfoRepository)
            }
        }
    }

    // ─── Service connection ───────────────────────────────────────────────────

    private var monitorBinder: MonitorService.MonitorBinder? = null

    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            monitorBinder = binder as MonitorService.MonitorBinder
            viewModelScope.launch {
                // Sync ViewModel FROM service — not the other way around.
                // If the service was already running (e.g. app restarted from notification),
                // its alertsEnabled state is the truth we should reflect in the UI.
                monitorBinder?.alertsEnabledFlow?.collect { enabled ->
                    _uiState.update { it.copy(alertsEnabled = enabled) }
                }
            }
            viewModelScope.launch {
                monitorBinder?.stateFlow?.collect { state ->
                    if (state != null) _uiState.update { it.copy(liveStatus = state.toLiveStatus()) }
                }
            }
            viewModelScope.launch {
                monitorBinder?.crossingMutedFlow?.collect { muted ->
                    _uiState.update { it.copy(crossingAlarmMuted = muted) }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            monitorBinder = null
            _uiState.update { it.copy(liveStatus = LiveAltitudeStatus()) }
        }
    }

    // ─── System info ──────────────────────────────────────────────────────────

    fun refreshSystemInfo() {
        val info: SystemInfo = systemInfoRepository.querySystemInfo()
        _uiState.update {
            it.copy(
                hasBarometer = info.hasBarometer,
                preferredSource = if (!info.hasBarometer) PreferredSource.GPS else it.preferredSource,
                fineLocationGranted = info.fineLocationGranted,
                coarseLocationGranted = info.coarseLocationGranted,
                notificationsGranted = info.notificationsGranted,
            )
        }
    }

    fun markPermissionsDenied() {
        _uiState.update { it.copy(permissionsPreviouslyDenied = true) }
    }

    // ─── Main screen actions ──────────────────────────────────────────────────

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.ToggleAlerts -> toggleAlerts()
            MainAction.NavigateToAdvancedSettings -> _uiState.update { it.copy(currentScreen = AppScreen.ADVANCED_SETTINGS) }
            MainAction.RequestPermissions -> Unit
            MainAction.OpenAppSettings -> Unit
            MainAction.MuteAlarm -> monitorBinder?.muteAlarm()
            is MainAction.SetPreferredSource -> updateAndPersist { it.copy(preferredSource = action.source) }
            is MainAction.UpdateBandLowerAltitude -> updateAndPersist {
                it.copy(
                    bandLowerAltitudeFeet = action.value
                )
            }

            is MainAction.UpdateBandUpperAltitude -> updateAndPersist {
                it.copy(
                    bandUpperAltitudeFeet = action.value
                )
            }

            is MainAction.UpdateQnh -> {
                _uiState.update { it.copy(qnhHpa = action.value) }
                action.value.toFloatOrNull()?.let { qnh ->
                    viewModelScope.launch { configRepository.updateQnh(qnh) }
                }
            }
        }
    }

    // ─── Advanced screen actions ──────────────────────────────────────────────

    fun onAdvancedAction(action: AdvancedAction) {
        when (action) {
            AdvancedAction.NavigateBack ->
                _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }

            is AdvancedAction.SetDistanceThresholdFeet ->
                updateAndPersist { it.copy(approachThresholdFeet = action.value) }

            is AdvancedAction.SetThresholdAlertEnabled ->
                updateAndPersist { it.copy(thresholdAlertEnabled = action.enabled) }

            is AdvancedAction.SetSoundEnabled ->
                updateAndPersist { it.copy(soundEnabled = action.enabled) }

            is AdvancedAction.SetVibrateEnabled ->
                updateAndPersist { it.copy(vibrateEnabled = action.enabled) }

            is AdvancedAction.SetWarnOnLowAccuracy ->
                _uiState.update { it.copy(warnOnLowAccuracy = action.enabled) }

            is AdvancedAction.SetMaxAltitudeAlertEnabled ->
                updateAndPersist { it.copy(maxAltitudeEnabled = action.enabled) }

            is AdvancedAction.UpdateMaxAltitudeThreshold ->
                updateAndPersist { it.copy(maxAltitudeThresholdFeet = action.value) }

            is AdvancedAction.UpdateMaxAltitudeMinAltitude ->
                updateAndPersist { it.copy(maxAltitudeMinAltitudeFeet = action.value) }

            is AdvancedAction.UpdateMaxAltitudeSilenceMinutes ->
                updateAndPersist { it.copy(maxAltitudeSilenceMinutes = action.value) }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun toggleAlerts() {
        val state = _uiState.value
        if (state.alertsEnabled) {
            setAlertsEnabled(false)
            return
        }
        when (state.monitorReadiness) {
            MonitorReadiness.MISSING_PERMISSIONS -> Unit
            MonitorReadiness.MISSING_ALTITUDE_SOURCE -> Unit
            MonitorReadiness.READY -> {
                if (state.isConfigValid && state.liveStatus.altitudeFeet != null) setAlertsEnabled(
                    true
                )
            }
        }
    }

    private fun setAlertsEnabled(enabled: Boolean) {
        // Push to service — the alertsEnabledFlow collector above will update UI state.
        // If not yet connected (no binder), update UI directly so the toggle feels responsive.
        if (monitorBinder != null) {
            monitorBinder?.setAlertsEnabled(enabled)
        } else {
            _uiState.update { it.copy(alertsEnabled = enabled) }
        }
    }

    private fun updateAndPersist(transform: (MainUiState) -> MainUiState) {
        _uiState.update(transform)
        viewModelScope.launch { configRepository.save(buildConfig(_uiState.value)) }
    }

    private fun buildConfig(s: MainUiState): AlertConfig = AlertConfig(
        lowerLimitFeet = s.bandLowerAltitudeFeet.toFloatOrNull() ?: 2800f,
        upperLimitFeet = s.bandUpperAltitudeFeet.toFloatOrNull() ?: 3200f,
        qnhHpa = s.qnhHpa.toFloatOrNull() ?: 1013.25f,
        preferredSource = s.preferredSource,
        approachThresholdFeet = s.approachThresholdFeet.toFloatOrNull() ?: 200f,
        maxAltitude = MaxAltitudeConfig(
            enabled = s.maxAltitudeEnabled,
            exceedanceMarginFeet = s.maxAltitudeThresholdFeet.toFloatOrNull() ?: 50f,
            minAltitudeFeet = s.maxAltitudeMinAltitudeFeet.toFloatOrNull() ?: 500f,
            silenceDurationMinutes = s.maxAltitudeSilenceMinutes.toIntOrNull() ?: 5,
        ),
        thresholdAlertEnabled = s.thresholdAlertEnabled,
        soundEnabled = s.soundEnabled,
        vibrateEnabled = s.vibrateEnabled,
    )

    private fun applyConfigToUiState(current: MainUiState, config: AlertConfig): MainUiState =
        current.copy(
            // alertsEnabled is intentionally not restored — alerts always start off.
            preferredSource = config.preferredSource,
            bandLowerAltitudeFeet = config.lowerLimitFeet.toInt().toString(),
            bandUpperAltitudeFeet = config.upperLimitFeet.toInt().toString(),
            qnhHpa = config.qnhHpa.toInt().toString(),
            approachThresholdFeet = config.approachThresholdFeet.toInt().toString(),
            maxAltitudeEnabled = config.maxAltitude.enabled,
            maxAltitudeThresholdFeet = config.maxAltitude.exceedanceMarginFeet.toInt().toString(),
            maxAltitudeMinAltitudeFeet = config.maxAltitude.minAltitudeFeet.toInt().toString(),
            maxAltitudeSilenceMinutes = config.maxAltitude.silenceDurationMinutes.toString(),
            thresholdAlertEnabled = config.thresholdAlertEnabled,
            soundEnabled = config.soundEnabled,
            vibrateEnabled = config.vibrateEnabled,
        )
}

private fun MonitorState.toLiveStatus() = LiveAltitudeStatus(
    altitudeFeet = altitudeFeet,
    flightLevel = flightLevel,
    alertResult = alertResult,
    altitudeSource = altitudeSource,
    gpsAccuracyStatus = gpsAccuracyStatus,
    gpsVerticalAccuracyFeet = gpsVerticalAccuracyFeet,
    sessionMaxFeet = sessionMaxFeet,
)