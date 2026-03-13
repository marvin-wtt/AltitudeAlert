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
import kotlinx.coroutines.flow.first
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
enum class ReferenceMode { ALTITUDE, FLIGHT_LEVEL }

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

data class AlarmConfig(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val repeatEnabled: Boolean = false,
    val repeatSeconds: String = "5",
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
    val referenceMode: ReferenceMode = ReferenceMode.ALTITUDE,

    val bandLowerAltitudeFeet: String = "2800",
    val bandUpperAltitudeFeet: String = "3200",
    val bandLowerFlightLevel: String = "60",
    val bandUpperFlightLevel: String = "70",
    val qnhHpa: String = "1013",

    val approachThresholdFeet: String = "200",

    val thresholdAlarm: AlarmConfig = AlarmConfig(),
    val crossingAlarm: AlarmConfig = AlarmConfig(repeatEnabled = true, repeatSeconds = "3"),

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
    val bandLowerFLValidation: ValidationResult
        get() = Validators.bandLowerFL(bandLowerFlightLevel, bandUpperFlightLevel)
    val bandUpperFLValidation: ValidationResult
        get() = Validators.bandUpperFL(bandUpperFlightLevel, bandLowerFlightLevel)
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
    val thresholdRepeatValidation: ValidationResult
        get() = if (thresholdAlarm.repeatEnabled) Validators.repeatSeconds(thresholdAlarm.repeatSeconds)
        else ValidationResult.Valid
    val crossingRepeatValidation: ValidationResult
        get() = if (crossingAlarm.repeatEnabled) Validators.repeatSeconds(crossingAlarm.repeatSeconds)
        else ValidationResult.Valid

    val isConfigValid: Boolean
        get() = listOf(
            qnhValidation,
            if (referenceMode == ReferenceMode.ALTITUDE) bandLowerAltitudeValidation else bandLowerFLValidation,
            if (referenceMode == ReferenceMode.ALTITUDE) bandUpperAltitudeValidation else bandUpperFLValidation,
            approachThresholdValidation,
            maxAltitudeThresholdValidation,
            maxAltitudeMinAltitudeValidation,
            maxAltitudeSilenceValidation,
            thresholdRepeatValidation,
            crossingRepeatValidation,
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
        // Always reset alertsEnabled to false on startup — both so the UI starts
        // off and so the service (which reads configFlow directly) agrees.
        viewModelScope.launch {
            val current = configRepository.configFlow.first()
            if (current.alertsEnabled) configRepository.save(current.copy(alertsEnabled = false))
        }
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

    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val monitorBinder = binder as MonitorService.MonitorBinder
            viewModelScope.launch {
                monitorBinder.stateFlow.collect { state ->
                    if (state != null) _uiState.update { it.copy(liveStatus = state.toLiveStatus()) }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
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
            is MainAction.SetReferenceMode -> updateAndPersist { it.copy(referenceMode = action.mode) }
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

            is MainAction.UpdateBandLowerFlightLevel -> updateAndPersist {
                it.copy(
                    bandLowerFlightLevel = action.value
                )
            }

            is MainAction.UpdateBandUpperFlightLevel -> updateAndPersist {
                it.copy(
                    bandUpperFlightLevel = action.value
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

            is AdvancedAction.SetThresholdSoundEnabled ->
                _uiState.update { it.copy(thresholdAlarm = it.thresholdAlarm.copy(soundEnabled = action.enabled)) }

            is AdvancedAction.SetThresholdVibrationEnabled ->
                updateAndPersist { it.copy(thresholdAlarm = it.thresholdAlarm.copy(vibrationEnabled = action.enabled)) }

            is AdvancedAction.SetThresholdRepeatEnabled ->
                _uiState.update { it.copy(thresholdAlarm = it.thresholdAlarm.copy(repeatEnabled = action.enabled)) }

            is AdvancedAction.UpdateThresholdRepeatSeconds ->
                _uiState.update { it.copy(thresholdAlarm = it.thresholdAlarm.copy(repeatSeconds = action.value)) }

            is AdvancedAction.SetCrossingSoundEnabled ->
                _uiState.update { it.copy(crossingAlarm = it.crossingAlarm.copy(soundEnabled = action.enabled)) }

            is AdvancedAction.SetCrossingVibrationEnabled ->
                updateAndPersist { it.copy(crossingAlarm = it.crossingAlarm.copy(vibrationEnabled = action.enabled)) }

            is AdvancedAction.SetCrossingRepeatEnabled ->
                _uiState.update { it.copy(crossingAlarm = it.crossingAlarm.copy(repeatEnabled = action.enabled)) }

            is AdvancedAction.UpdateCrossingRepeatSeconds ->
                _uiState.update { it.copy(crossingAlarm = it.crossingAlarm.copy(repeatSeconds = action.value)) }

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
            updateAndPersist { it.copy(alertsEnabled = false) }
            return
        }
        when (state.monitorReadiness) {
            MonitorReadiness.MISSING_PERMISSIONS -> Unit
            MonitorReadiness.MISSING_ALTITUDE_SOURCE -> Unit
            MonitorReadiness.READY -> {
                if (state.isConfigValid) updateAndPersist { it.copy(alertsEnabled = true) }
            }
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
        useFlightLevels = s.referenceMode == ReferenceMode.FLIGHT_LEVEL,
        preferredSource = s.preferredSource,
        approachThresholdFeet = s.approachThresholdFeet.toFloatOrNull() ?: 200f,
        maxAltitude = MaxAltitudeConfig(
            enabled = s.maxAltitudeEnabled,
            exceedanceMarginFeet = s.maxAltitudeThresholdFeet.toFloatOrNull() ?: 50f,
            minAltitudeFeet = s.maxAltitudeMinAltitudeFeet.toFloatOrNull() ?: 500f,
            silenceDurationMinutes = s.maxAltitudeSilenceMinutes.toIntOrNull() ?: 5,
        ),
        vibrate = s.thresholdAlarm.vibrationEnabled,
        alarmSoundUri = null,
        alertsEnabled = false, // always persisted as off; runtime state only
    )

    private fun applyConfigToUiState(current: MainUiState, config: AlertConfig): MainUiState =
        current.copy(
            // alertsEnabled is intentionally not restored — alerts always start off.
            preferredSource = config.preferredSource,
            referenceMode = if (config.useFlightLevels) ReferenceMode.FLIGHT_LEVEL else ReferenceMode.ALTITUDE,
            bandLowerAltitudeFeet = config.lowerLimitFeet.toInt().toString(),
            bandUpperAltitudeFeet = config.upperLimitFeet.toInt().toString(),
            bandLowerFlightLevel = (config.lowerLimitFeet / 100).toInt().toString(),
            bandUpperFlightLevel = (config.upperLimitFeet / 100).toInt().toString(),
            qnhHpa = config.qnhHpa.toInt().toString(),
            approachThresholdFeet = config.approachThresholdFeet.toInt().toString(),
            maxAltitudeEnabled = config.maxAltitude.enabled,
            maxAltitudeThresholdFeet = config.maxAltitude.exceedanceMarginFeet.toInt().toString(),
            maxAltitudeMinAltitudeFeet = config.maxAltitude.minAltitudeFeet.toInt().toString(),
            maxAltitudeSilenceMinutes = config.maxAltitude.silenceDurationMinutes.toString(),
            thresholdAlarm = current.thresholdAlarm.copy(vibrationEnabled = config.vibrate),
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