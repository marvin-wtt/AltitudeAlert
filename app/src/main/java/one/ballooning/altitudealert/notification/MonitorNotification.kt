package one.ballooning.altitudealert.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import one.ballooning.altitudealert.R
import one.ballooning.altitudealert.data.model.AlertConfig
import one.ballooning.altitudealert.domain.AlertStatus
import one.ballooning.altitudealert.domain.MonitorState
import one.ballooning.altitudealert.service.MonitorService
import one.ballooning.altitudealert.ui.MainActivity

class MonitorNotification(private val context: Context) {

    private val manager = context.getSystemService<NotificationManager>()!!

    // ── Live foreground notification ──────────────────────────────────────────

    fun buildInitial(): Notification =
        builder(CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle("Acquiring altitude…")
            .build()

    fun update(state: MonitorState, config: AlertConfig, alertsEnabled: Boolean, crossingMuted: Boolean) {
        val f = MonitorStatusFormatter.format(state, config, alertsEnabled)
        val overallStatus = state.alertResult.overallStatus
        val title: CharSequence = when (overallStatus) {
            AlertStatus.APPROACHING, AlertStatus.CROSSED ->
                SpannableString(f.title).also {
                    it.setSpan(
                        ForegroundColorSpan(context.getColor(f.colorRes)),
                        0, it.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            else -> f.title
        }
        val nb = builder(CHANNEL_LIVE)
            .setSmallIcon(f.iconRes)
            .setContentTitle(title)
            .setContentText(f.body)
            .setColor(context.getColor(f.colorRes))

        // Use ProgressStyle when we have an altitude reading — shows the band indicator.
        // Fall back to BigTextStyle when altitude is unavailable (no source or GPS lost).
        val progressStyle = if (alertsEnabled) buildBandProgressStyle(state, config) else null
        if (progressStyle != null) {
            nb.setStyle(progressStyle)
        } else {
            nb.setStyle(NotificationCompat.BigTextStyle().bigText(f.bigText))
        }

        val isCrossing = state.alertResult.overallStatus == AlertStatus.CROSSED
        if (isCrossing && !crossingMuted) {
            val muteIntent = PendingIntent.getService(
                context, REQ_MUTE_CROSSING,
                Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_MUTE_CROSSING
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nb.addAction(R.drawable.ic_notification_audio_off, "Mute alarm", muteIntent)
        }

        manager.notify(NOTIFICATION_ID_LIVE, nb.build())
    }


    private fun buildBandProgressStyle(
        state: MonitorState,
        config: AlertConfig,
    ): NotificationCompat.ProgressStyle? {
        val altitudeFeet = state.altitudeFeet ?: return null
        val lowerFeet = config.lowerLimitFeet
        val upperFeet = config.upperLimitFeet
        val thresholdFeet = if (config.thresholdAlertEnabled) config.approachThresholdFeet else 0f

        // ── Coordinate mapping (mirrors BandIndicator canvas logic) ───────────
        val bandRange = (upperFeet - lowerFeet).coerceAtLeast(1f)
        val margin = (bandRange * 0.30f).coerceAtLeast(150f)
        val rangeMin = lowerFeet - margin
        val rangeMax = upperFeet + margin
        val rangeWidth = rangeMax - rangeMin

        // Segment weights are integers; scale to 1000 for sub-percent precision.
        val scale = 1000
        fun toWeight(alt: Float) =
            ((alt - rangeMin) / rangeWidth * scale).toInt().coerceIn(0, scale)

        fun toFraction(alt: Float) =
            ((alt - rangeMin) / rangeWidth).coerceIn(0f, 1f)

        val wLo = toWeight(lowerFeet)
        val wHi = toWeight(upperFeet)
        val wLoT = toWeight((lowerFeet + thresholdFeet).coerceAtMost(upperFeet))
        val wHiT = toWeight((upperFeet - thresholdFeet).coerceAtLeast(lowerFeet))


        val bandArgb = context.getColor(
            when (state.alertResult.overallStatus) {
                AlertStatus.CROSSED -> R.color.notification_crossed
                AlertStatus.APPROACHING -> R.color.notification_approaching
                AlertStatus.CLEAR -> R.color.notification_clear
            }
        )
        val thresholdArgb = ColorUtils.blendARGB(bandArgb, 0xFFFFFFFF.toInt(), 0.50f)
        val outsideArgb = 0xFFBDBDBD.toInt()

        // ── Segments ─────────────────────────────────────────────────────────
        val segments = buildList {
            // Outside band — below lower limit
            if (wLo > 0)
                add(NotificationCompat.ProgressStyle.Segment(wLo).setColor(outsideArgb))
            // Lower threshold zone
            if (wLoT > wLo && config.thresholdAlertEnabled)
                add(NotificationCompat.ProgressStyle.Segment(wLoT - wLo).setColor(thresholdArgb))
            // Safe centre
            if (wHiT > wLoT)
                add(NotificationCompat.ProgressStyle.Segment(wHiT - wLoT).setColor(bandArgb))
            // Upper threshold zone
            if (wHi > wHiT && config.thresholdAlertEnabled)
                add(NotificationCompat.ProgressStyle.Segment(wHi - wHiT).setColor(thresholdArgb))
            // Outside band — above upper limit
            if (scale > wHi)
                add(NotificationCompat.ProgressStyle.Segment(scale - wHi).setColor(outsideArgb))
        }

        val altWeight = (toFraction(altitudeFeet) * scale).toInt()

        val trackerIcon = IconCompat.createWithResource(context, R.drawable.ic_tracker)

        return NotificationCompat.ProgressStyle()
            .setProgressSegments(segments)
            .setProgress(altWeight)
            .setProgressTrackerIcon(trackerIcon)
            .setStyledByProgress(false)   // we control colours entirely via segments
    }

    // ── Max altitude alarm notification ───────────────────────────────────────

    fun postMaxAltitudeAlarmNotification(sessionMaxFeet: Float?, alertAltitudeFeet: Float) {
        val ackIntent = PendingIntent.getService(
            context, REQ_ACKNOWLEDGE_MAX,
            Intent(context, MonitorService::class.java).apply {
                action = MonitorService.ACTION_ACKNOWLEDGE_MAX_ALTITUDE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = buildString {
            if (sessionMaxFeet != null) append("Max altitude: %,d ft".format(sessionMaxFeet.toInt()))
            append(" · Alert below: %,d ft".format(alertAltitudeFeet.toInt()))
        }
        val nb = NotificationCompat.Builder(context, CHANNEL_MAX_ALTITUDE)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Maximum altitude alert")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(context.getColor(R.color.notification_approaching))
            .setOngoing(true)           // persists until acknowledged
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tapIntent())
            .addAction(R.drawable.ic_notification_audio_off, "Acknowledge", ackIntent)
        manager.notify(NOTIFICATION_ID_MAX_ALTITUDE, nb.build())
    }

    fun cancelMaxAltitudeNotification() {
        manager.cancel(NOTIFICATION_ID_MAX_ALTITUDE)
    }

    // ── GPS lost notification ─────────────────────────────────────────────────

    fun postGpsLostNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_GPS_LOST)
            .setSmallIcon(R.drawable.ic_location_disabled)
            .setContentTitle("GPS signal lost")
            .setContentText("Altitude unavailable — band alerts are paused.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .build()

        manager.notify(NOTIFICATION_ID_GPS_LOST, notification)
    }

    fun cancelGpsLostNotification() {
        manager.cancel(NOTIFICATION_ID_GPS_LOST)
    }

    fun cancelLiveNotification() {
        manager.cancel(NOTIFICATION_ID_LIVE)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun builder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setContentIntent(tapIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    companion object {
        const val NOTIFICATION_ID_LIVE = 1
        const val NOTIFICATION_ID_MAX_ALTITUDE = 2
        const val NOTIFICATION_ID_GPS_LOST = 3

        private const val REQ_MUTE_CROSSING = 1
        private const val REQ_ACKNOWLEDGE_MAX = 2

        private const val CHANNEL_LIVE = "altitude_monitor"
        private const val CHANNEL_MAX_ALTITUDE = "max_altitude_alert"
        private const val CHANNEL_GPS_LOST = "gps_lost"

        fun createChannels(context: Context) {
            val manager = context.getSystemService<NotificationManager>()!!
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LIVE,
                    "Altitude Monitor",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live altitude while monitoring is active"
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_GPS_LOST,
                    "GPS Signal Lost",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when GPS signal is lost while monitoring is active"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MAX_ALTITUDE,
                    "Max Altitude Alert",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Continuous alarm when maximum altitude is approached"
                    setSound(null, null)   // sound handled by AlarmSoundPlayer
                    enableVibration(false) // vibration handled by MonitorService
                    setShowBadge(true)
                }
            )
        }
    }
}