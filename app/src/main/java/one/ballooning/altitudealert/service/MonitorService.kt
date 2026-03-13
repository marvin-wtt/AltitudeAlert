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
    }

    private val binder = MonitorBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ─── State ────────────────────────────────────────────────────────────────

    private val _stateFlow = MutableStateFlow<MonitorState?>(null)
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
        val app = application as AltitudeAlertApplication
        val notification = MonitorNotification(applicationContext)
        val monitor = AltitudeMonitor(
            readings = app.altitudeDataSource.readings,
            config = app.configRepository.configFlow,
        )

        MonitorNotification.createChannels(applicationContext)
        startForeground(MonitorNotification.NOTIFICATION_ID_LIVE, notification.buildInitial())

        val combined = monitor.monitorState()
            .combine(app.configRepository.configFlow) { state, config -> state to config }

        // Alerts fire on every emission — responsiveness matters here.
        scope.launch {
            combined.catch { e -> Log.e(TAG, "Flow error", e) }.collect { (state, config) ->
                    _stateFlow.value = state
                    handleBandAlerts(state, config)
                    handleMaxAltitudeAlert(state, config)
                }
            Log.w(TAG, "Collection ended unexpectedly")
        }

        // Notification updates are throttled — Android caps updates at 5/sec and
        // the user doesn't need sub-second altitude updates in the status bar.
        scope.launch {
            combined.sample(NOTIFICATION_UPDATE_INTERVAL_MS)
                .catch { e -> Log.e(TAG, "Notification flow error", e) }
                .collect { (state, config) -> notification.update(state, config) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SILENCE_MAX_ALTITUDE) {
            val minutes = intent.getIntExtra(EXTRA_SILENCE_MINUTES, 5)
            silencedUntilMs = System.currentTimeMillis() + minutes * 60_000L
            lastAlertedMaxFeet = _stateFlow.value?.sessionMaxFeet
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releasePlayer()
        super.onDestroy()
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
                playSound(
                    it, looping = current.overallStatus == AlertStatus.CROSSED
                )
            }
        }
        previousBandResult = current
    }

    private fun hasWorsened(prev: AlertStatus?, current: AlertStatus?): Boolean {
        if (current == null) return false
        return current.ordinal > (prev ?: AlertStatus.CLEAR).ordinal
    }

    // ─── Max altitude alert ───────────────────────────────────────────────────

    private fun handleMaxAltitudeAlert(state: MonitorState, config: AlertConfig) {
        val cfg = config.maxAltitude

        val isSilenced = silencedUntilMs?.let { it > System.currentTimeMillis() } ?: false
        if (!config.alertsEnabled || !cfg.enabled || isSilenced) {
            lastAlertedMaxFeet = state.sessionMaxFeet
            return
        }

        if (state.sessionMaxFeet < cfg.minAltitudeFeet) return
        val baseline = lastAlertedMaxFeet
        if (baseline != null && state.sessionMaxFeet <= baseline + cfg.exceedanceMarginFeet) return

        Log.d(TAG, "alertEnabled=${config.alertsEnabled}")

        lastAlertedMaxFeet = state.sessionMaxFeet
        silencedUntilMs = System.currentTimeMillis() + (10 * 60 * 1000)

        val notification = MonitorNotification(applicationContext)
        notification.postMaxAltitudeNotification(state, cfg.silenceDurationMinutes)
        if (config.vibrate) vibrate(AlertStatus.APPROACHING)
        config.alarmSoundUri?.let { playSound(it, looping = false) }
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
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
    }
}