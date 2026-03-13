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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import one.ballooning.altitudealert.AltitudeAlertApplication
import one.ballooning.altitudealert.data.model.AlertConfig
import one.ballooning.altitudealert.domain.AlertResult
import one.ballooning.altitudealert.domain.AlertStatus
import one.ballooning.altitudealert.domain.AltitudeMonitor
import one.ballooning.altitudealert.domain.MonitorState
import one.ballooning.altitudealert.notification.MonitorNotification

class MonitorService : Service() {

    // ─── Binder ───────────────────────────────────────────────────────────────

    inner class MonitorBinder : Binder() {
        val stateFlow: StateFlow<MonitorState?> get() = _stateFlow.asStateFlow()
        val crossingMutedFlow: StateFlow<Boolean> get() = _crossingMuted.asStateFlow()
        fun setAlertsEnabled(enabled: Boolean) {
            _alertsEnabled.value = enabled
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
    private var lastAlertedMaxFeet: Float? = null
    private var silencedUntilMs: Long? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getSystemService<VibratorManager>()!!.defaultVibrator
        else
            @Suppress("DEPRECATION") getSystemService<Vibrator>()!!
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
        startForeground(MonitorNotification.NOTIFICATION_ID_LIVE, notification.buildInitial())

        val combined = monitor.monitorState()
            .combine(app.configRepository.configFlow) { state, config -> state to config }

        scope.launch {
            combined
                .catch { e -> Log.e(TAG, "Flow error", e) }
                .collect { (state, config) ->
                    _stateFlow.value = state
                    handleBandAlerts(state, config)
                    handleMaxAltitudeAlert(state, config)
                }
            Log.w(TAG, "Collection ended unexpectedly")
        }

        scope.launch {
            combined
                .sample(NOTIFICATION_UPDATE_INTERVAL_MS)
                .catch { e -> Log.e(TAG, "Notification flow error", e) }
                .collect { (state, config) ->
                    notification.update(state, config, _crossingMuted.value)
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

    override fun onDestroy() {
        scope.cancel()
        soundPlayer.release()
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

    private fun silenceMaxAltitudeAlarm(minutes: Int) {
        silencedUntilMs = System.currentTimeMillis() + minutes * 60_000L
        lastAlertedMaxFeet = _stateFlow.value?.sessionMaxFeet
        notification.cancelMaxAltitudeNotification()
        Toast.makeText(
            this,
            "Max altitude alert silenced for $minutes min",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ─── Max altitude alert ───────────────────────────────────────────────────

    private fun handleMaxAltitudeAlert(state: MonitorState, config: AlertConfig) {
        val cfg = config.maxAltitude

        val isSilenced = silencedUntilMs?.let { it > System.currentTimeMillis() } ?: false
        if (!_alertsEnabled.value || !cfg.enabled || isSilenced) {
            lastAlertedMaxFeet = state.sessionMaxFeet
            return
        }

        if (state.sessionMaxFeet < cfg.minAltitudeFeet) return
        val baseline = lastAlertedMaxFeet
        if (baseline != null && state.sessionMaxFeet <= baseline + cfg.exceedanceMarginFeet) return

        lastAlertedMaxFeet = state.sessionMaxFeet
        // Always silence an alert for the next 1ßs to avoid alert spamming
        silencedUntilMs = System.currentTimeMillis() + (10 * 60 * 1000)

        notification.cancelMaxAltitudeNotification()
        notification.postMaxAltitudeNotification(state, cfg.silenceDurationMinutes)
        if (config.vibrateEnabled) vibrate(AlertStatus.APPROACHING)
        if (config.soundEnabled) soundPlayer.playThreshold()
    }


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