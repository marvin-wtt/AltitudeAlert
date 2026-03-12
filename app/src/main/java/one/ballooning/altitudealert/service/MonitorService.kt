package one.ballooning.altitudealert.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.getSystemService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import one.ballooning.altitudealert.data.AlertConfig
import one.ballooning.altitudealert.data.AppSettings
import one.ballooning.altitudealert.data.altitudeFlow
import one.ballooning.altitudealert.monitor.AlertResult
import one.ballooning.altitudealert.monitor.AlertStatus
import one.ballooning.altitudealert.monitor.AltitudeMonitor
import one.ballooning.altitudealert.monitor.MonitorState
import one.ballooning.altitudealert.notification.MonitorNotification

class MonitorService : Service() {

    // ─── Binding ──────────────────────────────────────────────────────────────

    inner class MonitorBinder : Binder() {
        val stateFlow: StateFlow<MonitorState?> get() = _stateFlow.asStateFlow()
    }

    private val binder = MonitorBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ─── Fields ───────────────────────────────────────────────────────────────

    private val _stateFlow = MutableStateFlow<MonitorState?>(null)

    private lateinit var settings: AppSettings
    private lateinit var monitor: AltitudeMonitor
    private lateinit var notification: MonitorNotification

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var mediaPlayer: MediaPlayer? = null
    private var previousBandResult: AlertResult? = null

    private var lastAlertedMaxFeet: Float? = null
    private var silencedUntilMs: Long? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService<VibratorManager>()!!.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService<Vibrator>()!!
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(applicationContext)
        monitor = AltitudeMonitor(altitudeFlow(applicationContext), settings)
        notification = MonitorNotification(applicationContext)
        MonitorNotification.createChannels(applicationContext)
        startForeground(MonitorNotification.NOTIFICATION_ID_LIVE, notification.buildInitial())
        scope.launch { collectMonitorState() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SILENCE_MAX_ALTITUDE -> {
                val minutes = intent.getIntExtra(EXTRA_SILENCE_MINUTES, 5)
                silencedUntilMs = System.currentTimeMillis() + minutes * 60_000L
                // Advance the baseline so a new alert requires climbing a full
                // margin above wherever the balloon is right now.
                lastAlertedMaxFeet = _stateFlow.value?.sessionMaxFeet
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releasePlayer()
        super.onDestroy()
    }

    // ─── Collection ───────────────────────────────────────────────────────────

    private suspend fun collectMonitorState() {
        monitor.monitorState().combine(settings.configFlow) { state, config -> state to config }
            .catch { e -> Log.e(TAG, "Flow error", e) }.collect { (state, config) ->
                _stateFlow.value = state
                notification.update(state)
                handleBandAlerts(state, config)
                handleMaxAltitudeAlert(state, config)
            }
        Log.w(TAG, "Collection ended unexpectedly")
    }

    // ─── Band alerts ──────────────────────────────────────────────────────────

    private fun handleBandAlerts(state: MonitorState, config: AlertConfig) {
        val current = state.alertResult
        val previous = previousBandResult
        val worsened = hasWorsened(
            previous?.lower?.status,
            current.lower?.status
        ) || hasWorsened(previous?.upper?.status, current.upper?.status)
        if (worsened) {
            if (config.vibrate) vibrate(current.overallStatus)
            config.alarmSoundUri?.let {
                playSound(it, looping = current.overallStatus == AlertStatus.CROSSED)
            }
        }
        previousBandResult = current
    }

    private fun hasWorsened(prev: AlertStatus?, current: AlertStatus?): Boolean {
        if (current == null) return false
        return current.ordinal > (prev ?: AlertStatus.CLEAR).ordinal
    }

    private fun handleMaxAltitudeAlert(state: MonitorState, config: AlertConfig) {
        val cfg = config.maxAltitude

        val isSilenced = silencedUntilMs?.let { it > System.currentTimeMillis() } ?: false
        if (!config.alertsEnabled || !cfg.enabled || isSilenced) {
            lastAlertedMaxFeet = state.sessionMaxFeet
            return
        }

        if (state.sessionMaxFeet < cfg.minAltitudeFeet) return

        val baseline = lastAlertedMaxFeet
        if (baseline == null || state.sessionMaxFeet > baseline + cfg.exceedanceMarginFeet) {
            lastAlertedMaxFeet = state.sessionMaxFeet
            notification.postMaxAltitudeNotification(state, cfg.silenceDurationMinutes)
            if (config.vibrate) vibrate(AlertStatus.APPROACHING)
            config.alarmSoundUri?.let { playSound(it, looping = false) }
        }
    }

    // ─── Sound & vibration ────────────────────────────────────────────────────

    private fun playSound(uriString: String, looping: Boolean) {
        releasePlayer()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            setDataSource(applicationContext, Uri.parse(uriString))
            isLooping = looping
            setOnPreparedListener { start() }
            setOnErrorListener { _, _, _ -> releasePlayer(); true }
            prepareAsync()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
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

        const val ACTION_SILENCE_MAX_ALTITUDE = "one.ballooning.altitudealert.SILENCE_MAX_ALTITUDE"
        const val EXTRA_SILENCE_MINUTES = "silence_minutes"

        private val VIBRATE_SHORT = longArrayOf(0, 150, 100, 150)
        private val VIBRATE_LONG = longArrayOf(0, 500, 150, 500, 150, 500)
    }
}