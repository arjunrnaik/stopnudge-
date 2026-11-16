package com.wakeme.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import org.osmdroid.config.Configuration

class WakeMeApp : Application() {

    companion object {
        const val TRACKING_CHANNEL_ID = "wakeme_tracking_channel"
        const val ALARM_CHANNEL_ID = "wakeme_alarm_channel"
        const val GENTLE_CHANNEL_ID = "wakeme_gentle_channel"
        private const val TAG = "WakeMeApp"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Configure OsmDroid User Agent & Cache directory
        // OpenStreetMap tile servers require an identifiable User-Agent to avoid 403 blocks
        try {
            val sharedPrefs = getSharedPreferences("wakeme_osmdroid", Context.MODE_PRIVATE)
            Configuration.getInstance().load(this, sharedPrefs)
            Configuration.getInstance().userAgentValue = packageName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OsmDroid configuration", e)
        }

        // 2. Register Notification Channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Silent Tracking Foreground Service Channel
            val trackingChannel = NotificationChannel(
                TRACKING_CHANNEL_ID,
                getString(R.string.tracking_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active distance monitoring and GPS tracking"
                setShowBadge(false)
            }

            // Loud Wake-Up Alarm Channel
            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            val alarmAudioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                getString(R.string.alarm_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wake-up alarms when approaching destination"
                setSound(alarmSoundUri, alarmAudioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 800)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Gentle Reminder Channel
            val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val gentleAudioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val gentleChannel = NotificationChannel(
                GENTLE_CHANNEL_ID,
                "Gentle Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gentle arrival notifications and custom notes"
                setSound(notificationSoundUri, gentleAudioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(trackingChannel)
            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(gentleChannel)
        }
    }
}
