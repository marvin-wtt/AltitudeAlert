package one.ballooning.altitudealert.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.hardware.Sensor
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.ballooning.altitudealert.data.AlertConfig
import one.ballooning.altitudealert.data.AppSettings
import one.ballooning.altitudealert.data.MaxAltitudeConfig
import one.ballooning.altitudealert.data.PreferredSource
import one.ballooning.altitudealert.monitor.AlertResult
import one.ballooning.altitudealert.monitor.AltitudeSourceType
import one.ballooning.altitudealert.monitor.GpsAccuracyStatus
import one.ballooning.altitudealert.monitor.MonitorState
import one.ballooning.altitudealert.service.MonitorService

// ─── Navigation ───────────────────────────────────────────────────────────────

enum class AppScreen { MAIN, ADVANCED_SETTINGS }
enum class ReferenceMode { ALTITUDE, FLIGHT_LEVEL }

// ─── Readiness ────────────────────────────────────────────────────────────────

enum class MonitorReadiness {
    READY,
    MISSING_PERMISSIONS,
    MISSING_ALTITUDE_SOURCE,
}

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

data class PermissionState(
    val fineLocationGranted: Boolean = false,
    val coarseLocationGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
) {
    val hasForegroundLocation: Boolean
        get() = fineLocationGranted || coarseLocationGranted
    val canMonitor: Boolean
        get() = hasForegroundLocation && notificationsGranted
}

// ─── UI state ─────────────────────────────────────────────────────────────────

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.MAIN,
    val alertsEnabled: Boolean = false,

    val permissions: PermissionState = PermissionState(),
    val showPermissionDialog: Boolean = false,
    val permissionsPreviouslyDenied: Boolean = false,   // true after launcher returns still-denied

    val hasBarometer: Boolean = false,
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

    // Max altitude alert config
    val maxAltitudeEnabled: Boolean = false,
    val maxAltitudeThresholdFeet: String = "50",
    val maxAltitudeMinAltitudeFeet: String = "500",
    val maxAltitudeSilenceMinutes: String = "5",

    val liveStatus: LiveAltitudeStatus = LiveAltitudeStatus(),
) {
    // ── Source / readiness ────────────────────────────────────────────────────

    val altitudeSourceAvailable: Boolean
        get() = hasBarometer || permissions.hasForegroundLocation

    val monitorReadiness: MonitorReadiness
        get() = when {
            !permissions.canMonitor -> MonitorReadiness.MISSING_PERMISSIONS
            !altitudeSourceAvailable -> MonitorReadiness.MISSING_ALTITUDE_SOURCE
            else -> MonitorReadiness.READY
        }

    // QNH is only relevant when using barometer as source
    val showQnh: Boolean
        get() = preferredSource == PreferredSource.BAROMETER || !hasBarometer

    // ── Main screen validation ────────────────────────────────────────────────

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

    // ── Advanced screen validation ────────────────────────────────────────────

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

    // ── Overall validity ──────────────────────────────────────────────────────

    val isConfigValid: Boolean
        get() = listOf(
            qnhValidation,
            if (referenceMode == ReferenceMode.ALTITUDE) bandLowerAltitudeValidation
            else bandLowerFLValidation,
            if (referenceMode == ReferenceMode.ALTITUDE) bandUpperAltitudeValidation
            else bandUpperFLValidation,
            approachThresholdValidation,
            maxAltitudeThresholdValidation,
            maxAltitudeMinAltitudeValidation,
            maxAltitudeSilenceValidation,
            thresholdRepeatValidation,
            crossingRepeatValidation,
        ).all { it.isValid }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    private val settings = AppSettings(application)

    // ─── Service connection ───────────────────────────────────────────────────

    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val monitorBinder = binder as MonitorService.MonitorBinder
            viewModelScope.launch {
                monitorBinder.stateFlow.collect { state ->
                    if (state != null) applyMonitorState(state)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            _uiState.update { it.copy(liveStatus = LiveAltitudeStatus()) }
        }
    }

    private fun applyMonitorState(state: MonitorState) {
        _uiState.update {
            it.copy(
                liveStatus = LiveAltitudeStatus(
                    altitudeFeet = state.altitudeFeet,
                    flightLevel = state.flightLevel,
                    alertResult = state.alertResult,
                    altitudeSource = state.altitudeSource,
                    gpsAccuracyStatus = state.gpsAccuracyStatus,
                    gpsVerticalAccuracyFeet = state.gpsVerticalAccuracyFeet,
                    sessionMaxFeet = state.sessionMaxFeet,
                )
            )
        }
    }

    // ─── Action dispatchers ───────────────────────────────────────────────────

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.ToggleAlerts -> toggleAlerts()
            MainAction.NavigateToAdvancedSettings -> navigateTo(AppScreen.ADVANCED_SETTINGS)
            MainAction.RequestPermissions -> Unit  // handled by AltitudeAlertApp callback
            MainAction.OpenAppSettings -> Unit  // handled by AltitudeAlertApp callback
            is MainAction.SetReferenceMode -> _uiState.update { it.copy(referenceMode = action.mode) }
            is MainAction.SetPreferredSource -> _uiState.update { it.copy(preferredSource = action.source) }
            is MainAction.UpdateBandLowerAltitude -> _uiState.update { it.copy(bandLowerAltitudeFeet = action.value) }
            is MainAction.UpdateBandUpperAltitude -> _uiState.update { it.copy(bandUpperAltitudeFeet = action.value) }
            is MainAction.UpdateBandLowerFlightLevel -> _uiState.update {
                it.copy(
                    bandLowerFlightLevel = action.value
                )
            }

            is MainAction.UpdateBandUpperFlightLevel -> _uiState.update {
                it.copy(
                    bandUpperFlightLevel = action.value
                )
            }

            is MainAction.UpdateQnh -> {
                _uiState.update { it.copy(qnhHpa = action.value) }
                action.value.toFloatOrNull()?.let { qnh ->
                    viewModelScope.launch { settings.updateQnh(qnh) }
                }
            }
        }
        when (action) {
            is MainAction.SetReferenceMode,
            is MainAction.SetPreferredSource,
            is MainAction.UpdateBandLowerAltitude,
            is MainAction.UpdateBandUpperAltitude,
            is MainAction.UpdateBandLowerFlightLevel,
            is MainAction.UpdateBandUpperFlightLevel -> persistConfig()

            else -> Unit
        }
    }

    fun onAdvancedAction(action: AdvancedAction) {
        when (action) {
            AdvancedAction.NavigateBack -> navigateTo(AppScreen.MAIN)
            is AdvancedAction.SetDistanceThresholdFeet -> _uiState.update {
                it.copy(
                    approachThresholdFeet = action.value
                )
            }

            is AdvancedAction.SetThresholdSoundEnabled -> _uiState.update {
                it.copy(
                    thresholdAlarm = it.thresholdAlarm.copy(
                        soundEnabled = action.enabled
                    )
                )
            }

            is AdvancedAction.SetThresholdVibrationEnabled -> _uiState.update {
                it.copy(
                    thresholdAlarm = it.thresholdAlarm.copy(vibrationEnabled = action.enabled)
                )
            }

            is AdvancedAction.SetThresholdRepeatEnabled -> _uiState.update {
                it.copy(
                    thresholdAlarm = it.thresholdAlarm.copy(
                        repeatEnabled = action.enabled
                    )
                )
            }

            is AdvancedAction.UpdateThresholdRepeatSeconds -> _uiState.update {
                it.copy(
                    thresholdAlarm = it.thresholdAlarm.copy(repeatSeconds = action.value)
                )
            }

            is AdvancedAction.SetCrossingSoundEnabled -> _uiState.update {
                it.copy(
                    crossingAlarm = it.crossingAlarm.copy(
                        soundEnabled = action.enabled
                    )
                )
            }

            is AdvancedAction.SetCrossingVibrationEnabled -> _uiState.update {
                it.copy(
                    crossingAlarm = it.crossingAlarm.copy(
                        vibrationEnabled = action.enabled
                    )
                )
            }

            is AdvancedAction.SetCrossingRepeatEnabled -> _uiState.update {
                it.copy(
                    crossingAlarm = it.crossingAlarm.copy(
                        repeatEnabled = action.enabled
                    )
                )
            }

            is AdvancedAction.UpdateCrossingRepeatSeconds -> _uiState.update {
                it.copy(
                    crossingAlarm = it.crossingAlarm.copy(
                        repeatSeconds = action.value
                    )
                )
            }

            is AdvancedAction.SetWarnOnLowAccuracy -> _uiState.update { it.copy(warnOnLowAccuracy = action.enabled) }
            is AdvancedAction.SetMaxAltitudeAlertEnabled -> _uiState.update {
                it.copy(
                    maxAltitudeEnabled = action.enabled
                )
            }

            is AdvancedAction.UpdateMaxAltitudeThreshold -> _uiState.update {
                it.copy(
                    maxAltitudeThresholdFeet = action.value
                )
            }

            is AdvancedAction.UpdateMaxAltitudeMinAltitude -> _uiState.update {
                it.copy(
                    maxAltitudeMinAltitudeFeet = action.value
                )
            }

            is AdvancedAction.UpdateMaxAltitudeSilenceMinutes -> _uiState.update {
                it.copy(
                    maxAltitudeSilenceMinutes = action.value
                )
            }
        }
        when (action) {
            is AdvancedAction.SetDistanceThresholdFeet,
            is AdvancedAction.SetThresholdVibrationEnabled,
            is AdvancedAction.SetCrossingVibrationEnabled,
            is AdvancedAction.SetMaxAltitudeAlertEnabled,
            is AdvancedAction.UpdateMaxAltitudeThreshold,
            is AdvancedAction.UpdateMaxAltitudeMinAltitude,
            is AdvancedAction.UpdateMaxAltitudeSilenceMinutes -> persistConfig()

            else -> Unit
        }
    }

    // ─── Sensor availability ──────────────────────────────────────────────────

    fun checkSensorAvailability(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasBarometer = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        _uiState.update {
            it.copy(
                hasBarometer = hasBarometer,
                // If no barometer, force GPS as source
                preferredSource = if (!hasBarometer) PreferredSource.GPS else it.preferredSource,
            )
        }
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    fun refreshPermissionState(context: Context) {
        _uiState.update {
            it.copy(
                permissions = PermissionState(
                    fineLocationGranted = isGranted(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    coarseLocationGranted = isGranted(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    notificationsGranted = isGranted(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                )
            )
        }
    }

    private fun isGranted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ─── Internals ────────────────────────────────────────────────────────────

    private fun navigateTo(screen: AppScreen) =
        _uiState.update { it.copy(currentScreen = screen) }

    private fun toggleAlerts() {
        val state = _uiState.value
        if (state.alertsEnabled) {
            _uiState.update { it.copy(alertsEnabled = false) }
            persistConfig()
            return
        }
        when (state.monitorReadiness) {
            MonitorReadiness.MISSING_PERMISSIONS -> _uiState.update { it.copy(showPermissionDialog = true) }
            MonitorReadiness.MISSING_ALTITUDE_SOURCE -> Unit  // button disabled
            MonitorReadiness.READY -> {
                if (state.isConfigValid) {
                    _uiState.update { it.copy(alertsEnabled = true) }
                    persistConfig()
                }
                // If config invalid, button is already disabled — unreachable
            }
        }
    }

    /** Called by MainActivity after the permission launcher returns still-denied. */
    fun markPermissionsDenied() {
        _uiState.update { it.copy(permissionsPreviouslyDenied = true) }
    }

    private fun persistConfig() {
        val s = _uiState.value
        val config = AlertConfig(
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
            alertsEnabled = s.alertsEnabled,
        )
        viewModelScope.launch { settings.saveConfig(config) }
    }
}