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

    val bandLowerAltitudeFeet: String = AlertConfig.DEFAULT.lowerLimitFeet.toInt().toString(),
    val bandUpperAltitudeFeet: String = AlertConfig.DEFAULT.upperLimitFeet.toInt().toString(),
    val qnhHpa: String = formatQnh(AlertConfig.DEFAULT.qnhHpa),

    val approachThresholdFeet: String = AlertConfig.DEFAULT.approachThresholdFeet.toInt()
        .toString(),

    val thresholdAlertEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val crossingAlarmMuted: Boolean = false,

    val warnOnLowAccuracy: Boolean = true,

    val maxAltitudeEnabled: Boolean = MaxAltitudeConfig.DEFAULT.enabled,
    val maxAltitudeAlertThresholdFeet: String = MaxAltitudeConfig.DEFAULT.alertThresholdFeet.toInt()
        .toString(),
    val maxAltitudeReactivationThresholdFeet: String = MaxAltitudeConfig.DEFAULT.reactivationThresholdFeet.toInt()
        .toString(),
    // Runtime state synced from service
    val maxAltitudeAlarmActive: Boolean = false,
    val maxAltitudeSilenced: Boolean = true,
    val sessionMaxTimestampMs: Long? = null,

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
    val maxAltitudeAlertThresholdValidation: ValidationResult
        get() = if (maxAltitudeEnabled) Validators.approachThreshold(maxAltitudeAlertThresholdFeet)
        else ValidationResult.Valid
    val maxAltitudeReactivationThresholdValidation: ValidationResult
        get() = if (maxAltitudeEnabled) Validators.approachThreshold(maxAltitudeReactivationThresholdFeet)
        else ValidationResult.Valid
    val isConfigValid: Boolean
        get() = listOf(
            qnhValidation,
            bandLowerAltitudeValidation,
            bandUpperAltitudeValidation,
            approachThresholdValidation,
            maxAltitudeAlertThresholdValidation,
            maxAltitudeReactivationThresholdValidation,
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
        // Pre-populate UI from persisted config once on startup.
        // Using first() (not collect) so the DataStore never overwrites what the user is typing.
        viewModelScope.launch {
            val config = configRepository.configFlow.first()
            _uiState.update { applyConfigToUiState(it, config) }
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
            viewModelScope.launch {
                monitorBinder?.maxAltitudeSilencedFlow?.collect { silenced ->
                    _uiState.update { it.copy(maxAltitudeSilenced = silenced) }
                }
            }
            viewModelScope.launch {
                monitorBinder?.maxAltitudeAlarmActiveFlow?.collect { active ->
                    _uiState.update { it.copy(maxAltitudeAlarmActive = active) }
                }
            }
            viewModelScope.launch {
                monitorBinder?.sessionMaxTimestampMsFlow?.collect { ts ->
                    _uiState.update { it.copy(sessionMaxTimestampMs = ts) }
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
            MainAction.AcknowledgeMaxAltitude -> monitorBinder?.acknowledgeMaxAltitude()
            MainAction.UnsilenceMaxAltitude -> monitorBinder?.unsilenceMaxAltitude()
            is MainAction.SetPreferredSource -> updateAndPersist { it.copy(preferredSource = action.source) }
            is MainAction.UpdateBandLowerAltitude -> {
                _uiState.update { it.copy(bandLowerAltitudeFeet = action.value) }
                if (action.value.toFloatOrNull() != null) persistIfValid()
            }

            is MainAction.UpdateBandUpperAltitude -> {
                _uiState.update { it.copy(bandUpperAltitudeFeet = action.value) }
                if (action.value.toFloatOrNull() != null) persistIfValid()
            }

            is MainAction.UpdateQnh -> {
                _uiState.update { it.copy(qnhHpa = action.value) }
                if (action.value.toFloatOrNull() != null) persistIfValid()
            }
        }
    }

    // ─── Advanced screen actions ──────────────────────────────────────────────

    fun onAdvancedAction(action: AdvancedAction) {
        when (action) {
            AdvancedAction.NavigateBack ->
                _uiState.update { it.copy(currentScreen = AppScreen.MAIN) }

            is AdvancedAction.SetDistanceThresholdFeet -> {
                _uiState.update { it.copy(approachThresholdFeet = action.value) }
                if (action.value.toFloatOrNull() != null) persistIfValid()
            }

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

            is AdvancedAction.UpdateMaxAltitudeAlertThreshold -> {
                _uiState.update { it.copy(maxAltitudeAlertThresholdFeet = action.value) }
                if (action.value.toFloatOrNull() != null) persistIfValid()
            }

            is AdvancedAction.UpdateMaxAltitudeReactivationThreshold -> {
                _uiState.update { it.copy(maxAltitudeReactivationThresholdFeet = action.value) }
                if (action.value.toFloatOrNull() != null) persistIfValid()
            }
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

    private fun persistIfValid() {
        val s = _uiState.value
        val allParseable = s.bandLowerAltitudeFeet.toFloatOrNull() != null &&
                s.bandUpperAltitudeFeet.toFloatOrNull() != null &&
                s.qnhHpa.toFloatOrNull() != null &&
                s.approachThresholdFeet.toFloatOrNull() != null &&
                s.maxAltitudeAlertThresholdFeet.toFloatOrNull() != null &&
                s.maxAltitudeReactivationThresholdFeet.toFloatOrNull() != null
        if (allParseable) viewModelScope.launch { configRepository.save(buildConfig(s)) }
    }

    // Note: toFloatOrNull() fallbacks should never trigger in practice — persistIfValid()
    // guarantees all fields are parseable before this is called. The fallbacks reference
    // AlertConfig.DEFAULT so there is only one source of truth for default values.
    private fun buildConfig(s: MainUiState): AlertConfig = AlertConfig(
        lowerLimitFeet = s.bandLowerAltitudeFeet.toFloatOrNull()
            ?: AlertConfig.DEFAULT.lowerLimitFeet,
        upperLimitFeet = s.bandUpperAltitudeFeet.toFloatOrNull()
            ?: AlertConfig.DEFAULT.upperLimitFeet,
        qnhHpa = s.qnhHpa.toFloatOrNull() ?: AlertConfig.DEFAULT.qnhHpa,
        preferredSource = s.preferredSource,
        approachThresholdFeet = s.approachThresholdFeet.toFloatOrNull()
            ?: AlertConfig.DEFAULT.approachThresholdFeet,
        maxAltitude = MaxAltitudeConfig(
            enabled = s.maxAltitudeEnabled,
            alertThresholdFeet = s.maxAltitudeAlertThresholdFeet.toFloatOrNull()
                ?: MaxAltitudeConfig.DEFAULT.alertThresholdFeet,
            reactivationThresholdFeet = s.maxAltitudeReactivationThresholdFeet.toFloatOrNull()
                ?: MaxAltitudeConfig.DEFAULT.reactivationThresholdFeet,
        ),
        thresholdAlertEnabled = s.thresholdAlertEnabled,
        soundEnabled = s.soundEnabled,
        vibrateEnabled = s.vibrateEnabled,
    )

    private fun applyConfigToUiState(current: MainUiState, config: AlertConfig): MainUiState =
        current.copy(
            preferredSource = config.preferredSource,
            bandLowerAltitudeFeet = config.lowerLimitFeet.toInt().toString(),
            bandUpperAltitudeFeet = config.upperLimitFeet.toInt().toString(),
            qnhHpa = formatQnh(config.qnhHpa),
            approachThresholdFeet = config.approachThresholdFeet.toInt().toString(),
            maxAltitudeEnabled = config.maxAltitude.enabled,
            maxAltitudeAlertThresholdFeet = config.maxAltitude.alertThresholdFeet.toInt()
                .toString(),
            maxAltitudeReactivationThresholdFeet = config.maxAltitude.reactivationThresholdFeet.toInt()
                .toString(),
            thresholdAlertEnabled = config.thresholdAlertEnabled,
            soundEnabled = config.soundEnabled,
            vibrateEnabled = config.vibrateEnabled,
        )
}

private fun formatQnh(hpa: Float): String =
    if (hpa % 1f == 0f) hpa.toInt().toString() else hpa.toString()

private fun MonitorState.toLiveStatus() = LiveAltitudeStatus(
    altitudeFeet = altitudeFeet,
    flightLevel = flightLevel,
    alertResult = alertResult,
    altitudeSource = altitudeSource,
    gpsAccuracyStatus = gpsAccuracyStatus,
    gpsVerticalAccuracyFeet = gpsVerticalAccuracyFeet,
    sessionMaxFeet = sessionMaxFeet,
)