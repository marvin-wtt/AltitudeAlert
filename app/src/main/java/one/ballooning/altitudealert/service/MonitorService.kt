package one.ballooning.altitudealert.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
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
        fun setAlertsEnabled(enabled: Boolean) {
            _alertsEnabled.value = enabled
            if (enabled) {
                // Explicitly start the service so it survives the app going to background.
                // BIND_AUTO_CREATE alone would let Android kill it when the activity unbinds.
                applicationContext.startService(
                    Intent(applicationContext, MonitorService::class.java)
                )
                startForeground(
                    MonitorNotification.NOTIFICATION_ID_LIVE, notification.buildInitial()
                )
            } else {
                resetCrossingAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        fun muteAlarm() {
            muteCrossingAlarm()
        }
    }

    private val binder = MonitorBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ─── State ────────────────────────────────────────────────────────────────

    private val _stateFlow = MutableStateFlow<MonitorState?>(null)
    private val _alertsEnabled = MutableStateFlow(false)
    private val _crossingMuted = MutableStateFlow(false)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var notification: MonitorNotification
    private lateinit var soundPlayer: AlarmSoundPlayer

    private var previousBandResult: AlertResult? = null
    private var previousGpsLost: Boolean = false
    private var lastAlertedMaxFeet: Float? = null
    private var silencedUntilMs: Long? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService<VibratorManager>()!!.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService<Vibrator>()!!
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

        // Cache the latest config so alert/notification handlers can read it without
        // re-combining — avoids an extra flow hop on every barometer/GPS emission.
        val latestConfig =
            app.configRepository.configFlow.stateIn(scope, SharingStarted.Eagerly, AlertConfig())

        scope.launch {
            monitor.monitorState().catch { e -> Log.e(TAG, "Flow error", e) }.collect { state ->
                    val config = latestConfig.value
                    _stateFlow.value = state
                    handleBandAlerts(state, config)
                    handleGpsLost(state)
                    handleMaxAltitudeAlert(state, config)
                }
            Log.w(TAG, "Collection ended unexpectedly")
        }

        scope.launch {
            monitor.monitorState().sample(NOTIFICATION_UPDATE_INTERVAL_MS)
                .catch { e -> Log.e(TAG, "Notification flow error", e) }.collect { state ->
                    notification.update(state, latestConfig.value, _crossingMuted.value)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MUTE_CROSSING -> muteCrossingAlarm()
            ACTION_SILENCE_MAX_ALTITUDE -> {
                val minutes = intent.getIntExtra(EXTRA_SILENCE_MINUTES, 5)
                silenceMaxAltitudeAlarm(minutes)
            }
        }
        return START_STICKY
    }

    override fun onUnbind(intent: Intent): Boolean {
        // If alerts are off when the app closes, there's nothing to keep alive.
        // If alerts are on, the service was explicitly started and stays running.
        if (!_alertsEnabled.value) stopSelf()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        soundPlayer.release()
        // If alerts are off the notification was never meant to persist — cancel it.
        // When alerts are on, stopForeground already removed it before stopSelf was called.
        if (!_alertsEnabled.value) notification.cancelLiveNotification()
        super.onDestroy()
    }

    // ─── Band alerts ──────────────────────────────────────────────────────────

    private fun handleBandAlerts(state: MonitorState, config: AlertConfig) {
        val current = state.alertResult
        val previous = previousBandResult
        previousBandResult = current

        if (!_alertsEnabled.value) return

        val prevOverall = previous?.overallStatus ?: AlertStatus.CLEAR
        val currOverall = current.overallStatus

        // Reset mute when altitude re-enters the band
        if (currOverall == AlertStatus.CLEAR && _crossingMuted.value) {
            _crossingMuted.value = false
        }

        when {
            // Newly crossed — start continuous alarm unless muted
            currOverall == AlertStatus.CROSSED && prevOverall != AlertStatus.CROSSED -> {
                if (!_crossingMuted.value) {
                    if (config.soundEnabled) soundPlayer.startCrossing()
                    if (config.vibrateEnabled) vibrate(AlertStatus.CROSSED)
                }
            }
            // Was crossing, now only approaching — stop alarm
            currOverall != AlertStatus.CROSSED && prevOverall == AlertStatus.CROSSED -> {
                soundPlayer.stopCrossing()
            }
            // Newly approaching from clear
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
    }

    private fun resetCrossingAlarm() {
        soundPlayer.stopCrossing()
        _crossingMuted.value = false
    }

    private fun silenceMaxAltitudeAlarm(minutes: Int) {
        silencedUntilMs = System.currentTimeMillis() + minutes * 60_000L
        lastAlertedMaxFeet = _stateFlow.value?.sessionMaxFeet
        notification.cancelMaxAltitudeNotification()
        Toast.makeText(
            this, "Max altitude alert silenced for $minutes min", Toast.LENGTH_SHORT
        ).show()
    }

    // ─── GPS lost notification ───────────────────────────────────────────────────

    private fun handleGpsLost(state: MonitorState) {
        if (!_alertsEnabled.value || state.altitudeSource != AltitudeSourceType.GPS) {
            // If alerts are off, cancel any lingering notification and reset state.
            if (previousGpsLost) {
                notification.cancelGpsLostNotification()
                previousGpsLost = false
            }
            return
        }
        val isLost = state.gpsAccuracyStatus == GpsAccuracyStatus.LOST
        if (isLost == previousGpsLost) return
        previousGpsLost = isLost
        if (isLost) {
            notification.postGpsLostNotification()
        } else {
            notification.cancelGpsLostNotification()
        }
    }

    // ─── Max altitude alert ───────────────────────────────────────────────────

    private fun handleMaxAltitudeAlert(state: MonitorState, config: AlertConfig) {
        val cfg = config.maxAltitude

        val isSilenced = silencedUntilMs?.let { it > System.currentTimeMillis() } ?: false
        if (!_alertsEnabled.value || !cfg.enabled || isSilenced) {
            lastAlertedMaxFeet = state.sessionMaxFeet
            return
        }

        val sessionMax = state.sessionMaxFeet ?: return
        if (sessionMax < cfg.minAltitudeFeet) return
        val baseline = lastAlertedMaxFeet
        if (baseline != null && sessionMax <= baseline + cfg.exceedanceMarginFeet) return

        lastAlertedMaxFeet = sessionMax
        // Always silence an alert for the next 1ßs to avoid alert spamming
        silencedUntilMs = System.currentTimeMillis() + (10 * 60 * 1000)

        notification.cancelMaxAltitudeNotification()
        notification.postMaxAltitudeNotification(state, cfg.silenceDurationMinutes)
        if (config.vibrateEnabled) vibrate(AlertStatus.APPROACHING)
        if (config.soundEnabled) soundPlayer.playThreshold()
    }

    // ─── Vibration ────────────────────────────────────────────────────────────

    private fun vibrate(status: AlertStatus) {
        val pattern = when (status) {
            AlertStatus.CLEAR -> return
            AlertStatus.APPROACHING -> VIBRATE_SHORT
            AlertStatus.CROSSED -> VIBRATE_LONG
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    companion object {
        private const val TAG = "MonitorService"
        const val ACTION_MUTE_CROSSING = "one.ballooning.altitudealert.MUTE_CROSSING"
        const val ACTION_SILENCE_MAX_ALTITUDE = "one.ballooning.altitudealert.SILENCE_MAX_ALTITUDE"
        const val EXTRA_SILENCE_MINUTES = "silence_minutes"
        private val VIBRATE_SHORT = longArrayOf(0, 150, 100, 150)
        private val VIBRATE_LONG = longArrayOf(0, 500, 150, 500, 150, 500)
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
    }
}