package one.ballooning.altitudealert.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
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
    private lateinit var vibrator: AlarmVibrator

    private var previousBandResult: AlertResult? = null
    private var previousGpsLost: Boolean = false

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        val app = application as AltitudeAlertApplication
        notification = MonitorNotification(applicationContext)
        soundPlayer = AlarmSoundPlayer(applicationContext)
        vibrator = AlarmVibrator(applicationContext)

        val monitor = AltitudeMonitor(
            readings = app.altitudeDataSource.readings,
            config = app.configRepository.configFlow,
            alertsEnabled = _alertsEnabled,
        )

        MonitorNotification.createChannels(applicationContext)

        val monitorFlow = monitor.monitorState()
            .catch { e -> Log.e(TAG, "Flow error", e) }
            .shareIn(scope, SharingStarted.Eagerly, replay = 1)

        // Combine monitorFlow with configFlow so config is always the real persisted value —
        // never a hardcoded default. monitorFlow itself only emits after configFlow has emitted
        // (via AltitudeMonitor's combine), so config is guaranteed to be real before any
        // alert handling or notification update runs.
        scope.launch {
            combine(monitorFlow, app.configRepository.configFlow) { state, config ->
                state to config
            }.collect { (state, config) ->
                _stateFlow.value = state
                trackSessionMax(state)
                handleBandAlerts(state, config)
                handleGpsLost(state)
                handleMaxAltitudeAlert(state, config)
            }
        }

        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            combine(monitorFlow, app.configRepository.configFlow, _crossingMuted) { state, config, muted ->
                Triple(state, config, muted)
            }.sample(NOTIFICATION_UPDATE_INTERVAL_MS).collect { (state, config, muted) ->
                notification.update(state, config, _alertsEnabled.value, muted)
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
        vibrator.cancel()
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
                    if (config.vibrateEnabled) vibrator.startCrossing()
                }
            }

            currOverall != AlertStatus.CROSSED && prevOverall == AlertStatus.CROSSED -> {
                soundPlayer.stopCrossing()
                vibrator.stopCrossing()
            }

            currOverall == AlertStatus.APPROACHING && prevOverall == AlertStatus.CLEAR -> {
                if (config.thresholdAlertEnabled) {
                    if (config.soundEnabled) soundPlayer.playThreshold()
                    if (config.vibrateEnabled) vibrator.playApproaching()
                }
            }
        }
    }

    private fun muteCrossingAlarm() {
        _crossingMuted.value = true
        soundPlayer.stopCrossing()
        vibrator.stopCrossing()
        // Notification updates automatically via the combine collector when _crossingMuted changes.
    }

    private fun resetCrossingAlarm() {
        soundPlayer.stopCrossing()
        vibrator.stopCrossing()
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

        // Lift silence when balloon has descended far enough below the session max.
        if (_maxAltitudeSilenced.value && !_maxAltitudeAlarmActive.value &&
            currentAlt <= reactivationAltitude
        ) {
            _maxAltitudeSilenced.value = false
        }

        // Max altitude monitoring runs regardless of band alert state.
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
        if (config.vibrateEnabled) vibrator.startMaxAltitude()
    }

    private fun acknowledgeMaxAltitude() {
        if (!_maxAltitudeAlarmActive.value) return
        _maxAltitudeAlarmActive.value = false
        _maxAltitudeSilenced.value = true

        soundPlayer.stopMaxAltitude()
        vibrator.stopMaxAltitude()

        notification.cancelMaxAltitudeNotification()
    }

    private fun stopMaxAltitudeAlarm() {
        if (!_maxAltitudeAlarmActive.value) return
        _maxAltitudeAlarmActive.value = false
        soundPlayer.stopMaxAltitude()
        vibrator.stopMaxAltitude()
        notification.cancelMaxAltitudeNotification()
    }

    companion object {
        private const val TAG = "MonitorService"
        const val ACTION_MUTE_CROSSING = "one.ballooning.altitudealert.MUTE_CROSSING"
        const val ACTION_ACKNOWLEDGE_MAX_ALTITUDE =
            "one.ballooning.altitudealert.ACKNOWLEDGE_MAX_ALTITUDE"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
    }
}