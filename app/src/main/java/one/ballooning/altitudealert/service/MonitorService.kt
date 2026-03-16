package one.ballooning.altitudealert.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.ballooning.altitudealert.AltitudeAlertApplication
import one.ballooning.altitudealert.data.model.AlertConfig
import one.ballooning.altitudealert.domain.AlertResult
import one.ballooning.altitudealert.domain.AlertStatus
import one.ballooning.altitudealert.domain.AltitudeMonitor
import one.ballooning.altitudealert.domain.AltitudeSourceType
import one.ballooning.altitudealert.domain.GpsAccuracyStatus
import one.ballooning.altitudealert.domain.MonitorState
import one.ballooning.altitudealert.notification.MonitorNotification

class MonitorService : Service() {

    // ─── Binder ───────────────────────────────────────────────────────────────

    inner class MonitorBinder : Binder() {
        val stateFlow: StateFlow<MonitorState?> get() = _stateFlow.asStateFlow()
        val crossingMutedFlow: StateFlow<Boolean> get() = _crossingMuted.asStateFlow()
        val alertsEnabledFlow: StateFlow<Boolean> get() = _alertsEnabled.asStateFlow()
        val maxAltitudeSilencedFlow: StateFlow<Boolean> get() = _maxAltitudeSilenced.asStateFlow()
        val maxAltitudeAlarmActiveFlow: StateFlow<Boolean> get() = _maxAltitudeAlarmActive.asStateFlow()
        val sessionMaxTimestampMsFlow: StateFlow<Long?> get() = _sessionMaxTimestampMs.asStateFlow()

        fun setAlertsEnabled(enabled: Boolean) {
            _alertsEnabled.value = enabled
            if (enabled) {
                applicationContext.startService(
                    Intent(applicationContext, MonitorService::class.java)
                )
                startForeground(
                    MonitorNotification.NOTIFICATION_ID_LIVE, notification.buildInitial()
                )
            } else {
                resetCrossingAlarm()
                stopMaxAltitudeAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        fun muteAlarm() = muteCrossingAlarm()
        fun acknowledgeMaxAltitude() = this@MonitorService.acknowledgeMaxAltitude()
        fun unsilenceMaxAltitude() {
            _maxAltitudeSilenced.value = false
        }
    }

    private val binder = MonitorBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ─── State ────────────────────────────────────────────────────────────────

    private val _stateFlow = MutableStateFlow<MonitorState?>(null)
    private val _alertsEnabled = MutableStateFlow(false)
    private val _crossingMuted = MutableStateFlow(false)

    // Max altitude alarm starts silenced — avoids a false alarm on first fix.
    private val _maxAltitudeSilenced = MutableStateFlow(true)
    private val _maxAltitudeAlarmActive = MutableStateFlow(false)
    private val _sessionMaxTimestampMs = MutableStateFlow<Long?>(null)
    private var previousSessionMaxFeet: Float? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var notification: MonitorNotification
    private lateinit var soundPlayer: AlarmSoundPlayer

    private var previousBandResult: AlertResult? = null
    private var previousGpsLost: Boolean = false

    private var crossingVibrationWanted = false
    private var maxAltitudeVibrationWanted = false

    private val vibrator: Vibrator by lazy {
       getSystemService<VibratorManager>()!!.defaultVibrator
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        val app = application as AltitudeAlertApplication
        notification = MonitorNotification(applicationContext)
        soundPlayer = AlarmSoundPlayer(scope)

        val monitor = AltitudeMonitor(
            readings = app.altitudeDataSource.readings,
            config = app.configRepository.configFlow,
            alertsEnabled = _alertsEnabled,
        )

        MonitorNotification.createChannels(applicationContext)

        val latestConfig = app.configRepository.configFlow
            .stateIn(scope, SharingStarted.Eagerly, AlertConfig())

        scope.launch {
            monitor.monitorState()
                .catch { e -> Log.e(TAG, "Flow error", e) }
                .collect { state ->
                    val config = latestConfig.value
                    _stateFlow.value = state
                    trackSessionMax(state)
                    handleBandAlerts(state, config)
                    handleGpsLost(state)
                    handleMaxAltitudeAlert(state, config)
                }
        }

        scope.launch {
            monitor.monitorState()
                .sample(NOTIFICATION_UPDATE_INTERVAL_MS)
                .catch { e -> Log.e(TAG, "Notification flow error", e) }
                .collect { state ->
                    notification.update(state, latestConfig.value, _crossingMuted.value)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MUTE_CROSSING -> muteCrossingAlarm()
            ACTION_ACKNOWLEDGE_MAX_ALTITUDE -> acknowledgeMaxAltitude()
        }
        return START_STICKY
    }

    override fun onUnbind(intent: Intent): Boolean {
        if (!_alertsEnabled.value) stopSelf()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        soundPlayer.release()
        if (!_alertsEnabled.value) notification.cancelLiveNotification()
        super.onDestroy()
    }

    // ─── Session max tracking ────────────────────────────────────────────────

    private fun trackSessionMax(state: MonitorState) {
        val current = state.sessionMaxFeet ?: return
        val prev = previousSessionMaxFeet
        if (prev == null || current > prev) {
            previousSessionMaxFeet = current
            _sessionMaxTimestampMs.value = System.currentTimeMillis()
        }
    }

    // ─── Band alerts ──────────────────────────────────────────────────────────

    private fun handleBandAlerts(state: MonitorState, config: AlertConfig) {
        val current = state.alertResult
        val previous = previousBandResult
        previousBandResult = current

        if (!_alertsEnabled.value) return

        val prevOverall = previous?.overallStatus ?: AlertStatus.CLEAR
        val currOverall = current.overallStatus

        if (currOverall == AlertStatus.CLEAR && _crossingMuted.value) {
            _crossingMuted.value = false
        }

        when {
            currOverall == AlertStatus.CROSSED && prevOverall != AlertStatus.CROSSED -> {
                if (!_crossingMuted.value) {
                    if (config.soundEnabled) soundPlayer.startCrossing()
                    if (config.vibrateEnabled) setCrossingVibration(true)
                }
            }

            currOverall != AlertStatus.CROSSED && prevOverall == AlertStatus.CROSSED -> {
                soundPlayer.stopCrossing()
                setCrossingVibration(false)
            }

            currOverall == AlertStatus.APPROACHING && prevOverall == AlertStatus.CLEAR -> {
                if (config.thresholdAlertEnabled) {
                    if (config.soundEnabled) soundPlayer.playThreshold()
                    if (config.vibrateEnabled) vibrate(AlertStatus.APPROACHING)
                }
            }
        }
    }

    private fun muteCrossingAlarm() {
        _crossingMuted.value = true
        soundPlayer.stopCrossing()
        setCrossingVibration(false)
    }

    private fun resetCrossingAlarm() {
        soundPlayer.stopCrossing()
        setCrossingVibration(false)
        _crossingMuted.value = false
    }

    // ─── GPS lost notification ────────────────────────────────────────────────

    private fun handleGpsLost(state: MonitorState) {
        if (!_alertsEnabled.value || state.altitudeSource != AltitudeSourceType.GPS) {
            if (previousGpsLost) {
                notification.cancelGpsLostNotification()
                previousGpsLost = false
            }
            return
        }
        val isLost = state.gpsAccuracyStatus == GpsAccuracyStatus.LOST
        if (isLost == previousGpsLost) return
        previousGpsLost = isLost
        if (isLost) notification.postGpsLostNotification()
        else notification.cancelGpsLostNotification()
    }

    // ─── Max altitude alarm ───────────────────────────────────────────────────

    private fun handleMaxAltitudeAlert(state: MonitorState, config: AlertConfig) {
        val cfg = config.maxAltitude
        if (!cfg.enabled || !_alertsEnabled.value) {
            if (_maxAltitudeAlarmActive.value) stopMaxAltitudeAlarm()
            return
        }

        val currentAlt = state.altitudeFeet ?: return
        val sessionMax = state.sessionMaxFeet ?: return

        val alertAltitude = sessionMax - cfg.alertThresholdFeet
        val reactivationAltitude = sessionMax - cfg.reactivationThresholdFeet

        // Lift silence when balloon has descended far enough below the max
        if (_maxAltitudeSilenced.value && !_maxAltitudeAlarmActive.value &&
            currentAlt <= reactivationAltitude
        ) {
            _maxAltitudeSilenced.value = false
        }

        // Trigger alarm when not silenced and altitude reaches the alert altitude
        // Note: max altitude monitoring intentionally runs regardless of band alert state.
        if (!_maxAltitudeSilenced.value && !_maxAltitudeAlarmActive.value &&
            currentAlt >= alertAltitude
        ) {
            startMaxAltitudeAlarm(state, config)
        }
    }

    private fun startMaxAltitudeAlarm(state: MonitorState, config: AlertConfig) {
        _maxAltitudeAlarmActive.value = true

        val cfg = config.maxAltitude
        val alertAlt = (state.sessionMaxFeet ?: 0f) - cfg.alertThresholdFeet
        notification.postMaxAltitudeAlarmNotification(state.sessionMaxFeet, alertAlt)

        if (config.soundEnabled) soundPlayer.startMaxAltitude()
        if (config.vibrateEnabled) setMaxAltitudeVibration(true)
    }

    private fun acknowledgeMaxAltitude() {
        if (!_maxAltitudeAlarmActive.value) return
        _maxAltitudeAlarmActive.value = false
        _maxAltitudeSilenced.value = true

        soundPlayer.stopMaxAltitude()
        setMaxAltitudeVibration(false)

        notification.cancelMaxAltitudeNotification()
    }

    private fun stopMaxAltitudeAlarm() {
        if (!_maxAltitudeAlarmActive.value) return
        _maxAltitudeAlarmActive.value = false
        soundPlayer.stopMaxAltitude()
        setMaxAltitudeVibration(false)
        notification.cancelMaxAltitudeNotification()
    }

    // ─── Vibration ────────────────────────────────────────────────────────────

    private fun vibrate(status: AlertStatus) {
        val pattern = when (status) {
            AlertStatus.CLEAR -> return
            AlertStatus.APPROACHING -> VIBRATE_SHORT
            AlertStatus.CROSSED -> return
        }
        vibrateWithAlarmPriority(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun setCrossingVibration(wanted: Boolean) {
        crossingVibrationWanted = wanted
        syncVibration()
    }

    private fun setMaxAltitudeVibration(wanted: Boolean) {
        maxAltitudeVibrationWanted = wanted
        syncVibration()
    }

    private fun syncVibration() {
        if (crossingVibrationWanted || maxAltitudeVibrationWanted) {
            vibrateWithAlarmPriority(VibrationEffect.createWaveform(VIBRATE_CROSSING_CYCLE, 0))
        } else {
            vibrator.cancel()
        }
    }

    private fun vibrateWithAlarmPriority(effect: VibrationEffect) {
        vibrator.vibrate(
            effect,
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    companion object {
        private const val TAG = "MonitorService"
        const val ACTION_MUTE_CROSSING = "one.ballooning.altitudealert.MUTE_CROSSING"
        const val ACTION_ACKNOWLEDGE_MAX_ALTITUDE =
            "one.ballooning.altitudealert.ACKNOWLEDGE_MAX_ALTITUDE"
        private val VIBRATE_SHORT = longArrayOf(0, 150, 100, 150)
        private val VIBRATE_CROSSING_CYCLE = longArrayOf(0, 400, 200, 400, 200, 400)
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
    }
}