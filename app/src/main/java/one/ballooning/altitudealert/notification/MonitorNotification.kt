package one.ballooning.altitudealert.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import one.ballooning.altitudealert.R
import one.ballooning.altitudealert.data.model.AlertConfig
import one.ballooning.altitudealert.domain.MonitorState
import one.ballooning.altitudealert.service.MonitorService
import one.ballooning.altitudealert.ui.MainActivity

class MonitorNotification(private val context: Context) {

    private val manager = context.getSystemService<NotificationManager>()!!

    // ── Live foreground notification ──────────────────────────────────────────

    fun buildInitial(): Notification =
        builder(CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification_clear)
            .setContentTitle("Altitude Alert")
            .setContentText("Acquiring altitude…")
            .build()

    fun update(state: MonitorState, config: AlertConfig, crossingMuted: Boolean) {
        val f = MonitorStatusFormatter.format(state, config)
        val nb = builder(CHANNEL_LIVE)
            .setSmallIcon(f.iconRes)
            .setContentTitle(f.title)
            .setContentText(f.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(f.bigText))
            .setColor(context.getColor(f.colorRes))
            .setColorized(true)

        val isCrossing = state.alertResult.overallStatus ==
                one.ballooning.altitudealert.domain.AlertStatus.CROSSED
        if (isCrossing && !crossingMuted) {
            val muteIntent = PendingIntent.getService(
                context, REQ_MUTE_CROSSING,
                Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_MUTE_CROSSING
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nb.addAction(R.drawable.ic_notification_clear, "Mute alarm", muteIntent)
        }

        manager.notify(NOTIFICATION_ID_LIVE, nb.build())
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
            if (sessionMaxFeet != null) append("Session max: %,d ft".format(sessionMaxFeet.toInt()))
            append(" · Alert below: %,d ft".format(alertAltitudeFeet.toInt()))
        }
        val nb = NotificationCompat.Builder(context, CHANNEL_MAX_ALTITUDE)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle("Maximum altitude alert")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(context.getColor(R.color.notification_approaching))
            .setColorized(true)
            .setOngoing(true)           // persists until acknowledged
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tapIntent())
            .addAction(R.drawable.ic_notification_clear, "Acknowledge", ackIntent)
        manager.notify(NOTIFICATION_ID_MAX_ALTITUDE, nb.build())
    }

    fun cancelMaxAltitudeNotification() {
        manager.cancel(NOTIFICATION_ID_MAX_ALTITUDE)
    }

    // ── GPS lost notification ─────────────────────────────────────────────────

    fun postGpsLostNotification() {
        val nb = NotificationCompat.Builder(context, CHANNEL_GPS_LOST)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle("GPS signal lost")
            .setContentText("Altitude unavailable — band alerts are paused.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
        manager.notify(NOTIFICATION_ID_GPS_LOST, nb.build())
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