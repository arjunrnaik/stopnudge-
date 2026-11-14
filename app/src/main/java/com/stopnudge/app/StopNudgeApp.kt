package com.stopnudge.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings

class StopNudgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Channel 1: Ongoing tracking notification (Low/Default importance)
            val trackingChannel = NotificationChannel(
                CHANNEL_TRACKING,
                getString(R.string.tracking_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live remaining distance while tracking is active"
                setShowBadge(false)
            }

            // Channel 2: Loud destination wake-up alarm (High importance)
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM,
                getString(R.string.alarm_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud wake-up alert when approaching your destination"
                enableVibration(true)
                setBypassDnd(true)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttributes)
            }

            notificationManager.createNotificationChannel(trackingChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    companion object {
        const val CHANNEL_TRACKING = "stopnudge_tracking_channel"
        const val CHANNEL_ALARM = "stopnudge_alarm_channel"
    }
}
