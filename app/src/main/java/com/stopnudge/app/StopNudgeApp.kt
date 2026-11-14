package com.stopnudge.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import java.io.File

class StopNudgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupCrashLogging()
        createNotificationChannels()
    }

    private fun setupCrashLogging() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("StopNudgeCrash", "FATAL CRASH in thread ${thread.name}: ${throwable.message}", throwable)
                val crashFile = File(filesDir, "last_crash.txt")
                crashFile.writeText(Log.getStackTraceString(throwable))
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = getSystemService(NotificationManager::class.java) ?: return

                // Channel 1: Ongoing tracking notification (Low importance, non-intrusive)
                val trackingChannel = NotificationChannel(
                    CHANNEL_TRACKING,
                    getString(R.string.tracking_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows live remaining distance while tracking is active"
                    setShowBadge(false)
                }

                // Channel 2: Loud destination wake-up alarm (High importance)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                val alarmChannel = NotificationChannel(
                    CHANNEL_ALARM,
                    getString(R.string.alarm_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Loud wake-up alert when approaching your destination"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1000)
                    if (alarmSound != null) {
                        setSound(alarmSound, audioAttributes)
                    }
                }

                notificationManager.createNotificationChannel(trackingChannel)
                notificationManager.createNotificationChannel(alarmChannel)
            } catch (e: Exception) {
                Log.e("StopNudgeApp", "Error creating notification channels", e)
            }
        }
    }

    companion object {
        const val CHANNEL_TRACKING = "stopnudge_tracking_channel"
        const val CHANNEL_ALARM = "stopnudge_alarm_channel"
    }
}
