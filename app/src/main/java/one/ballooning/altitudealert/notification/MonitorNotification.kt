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

    fun buildInitial(): Notification =
        builder(CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification_clear)
            .setContentTitle("Altitude Alert")
            .setContentText("Acquiring altitude…")
            .build()

    fun update(state: MonitorState, config: AlertConfig, crossingMuted: Boolean) {
        val f = MonitorStatusFormatter.format(state, config)
        val builder = builder(CHANNEL_LIVE)
            .setSmallIcon(f.iconRes)
            .setContentTitle(f.title)
            .setContentText(f.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(f.bigText))
            .setColor(context.getColor(f.colorRes))
            .setColorized(true)

        // Show mute button only when the crossing alarm is actively sounding
        val isCrossing = state.alertResult.overallStatus ==
                one.ballooning.altitudealert.domain.AlertStatus.CROSSED
        if (isCrossing && !crossingMuted) {
            val muteIntent = PendingIntent.getService(
                context, 1,
                Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_MUTE_CROSSING
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.ic_notification_clear, "Mute alarm", muteIntent)
        }

        manager.notify(NOTIFICATION_ID_LIVE, builder.build())
    }

    fun postMaxAltitudeNotification(state: MonitorState, silenceMinutes: Int) {
        val silenceIntent = PendingIntent.getService(
            context, 0,
            Intent(context, MonitorService::class.java).apply {
                action = MonitorService.ACTION_SILENCE_MAX_ALTITUDE
                putExtra(MonitorService.EXTRA_SILENCE_MINUTES, silenceMinutes)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            NOTIFICATION_ID_MAX_ALTITUDE,
            NotificationCompat.Builder(context, CHANNEL_MAX_ALTITUDE)
                .setSmallIcon(R.drawable.ic_notification_warning)
                .setContentTitle("Maximum altitude exceeded")
                .setContentText(state.sessionMaxFeet?.let { "Session max: %,d ft".format(it.toInt()) }
                    ?: "Monitoring active")
                .setColor(context.getColor(R.color.notification_approaching))
                .setColorized(true)
                .setOngoing(false)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                    R.drawable.ic_notification_clear,
                    "Silence for $silenceMinutes min",
                    silenceIntent
                )
                .build()
        )
    }

    fun postGpsLostNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_GPS_LOST)
            .setSmallIcon(R.drawable.ic_notification_warning)
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

    fun cancelMaxAltitudeNotification() {
        manager.cancel(NOTIFICATION_ID_MAX_ALTITUDE)
    }

    private fun builder(channelId: String): NotificationCompat.Builder {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, channelId)
            .setContentIntent(tapIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    companion object {
        const val NOTIFICATION_ID_LIVE = 1
        const val NOTIFICATION_ID_MAX_ALTITUDE = 2
        const val NOTIFICATION_ID_GPS_LOST = 3
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
                    enableVibration(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MAX_ALTITUDE,
                    "Max Altitude Alert",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alert when maximum altitude is exceeded"
                    setShowBadge(true)
                }
            )
        }
    }
}