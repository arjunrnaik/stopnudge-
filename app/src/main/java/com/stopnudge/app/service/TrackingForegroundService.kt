package com.stopnudge.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.stopnudge.app.MainActivity
import com.stopnudge.app.R
import com.stopnudge.app.StopNudgeApp
import com.stopnudge.app.alarm.AlarmController
import com.stopnudge.app.tracking.LocationTrackingManager

class TrackingForegroundService : Service() {

    private lateinit var trackingManager: LocationTrackingManager
    private lateinit var alarmController: AlarmController

    private var destinationName: String = "Destination"
    private var alertRadiusMeters: Double = 500.0
    private var hasAlarmTriggered: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alarmController = AlarmController(this)
        trackingManager = LocationTrackingManager(this) { _, distanceMeters ->
            handleDistanceUpdate(distanceMeters)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarmAndService()
            return START_NOT_STICKY
        }

        destinationName = intent?.getStringExtra(EXTRA_DEST_NAME) ?: "Destination"
        val destLat = intent?.getDoubleExtra(EXTRA_DEST_LAT, 0.0) ?: 0.0
        val destLon = intent?.getDoubleExtra(EXTRA_DEST_LON, 0.0) ?: 0.0
        alertRadiusMeters = intent?.getDoubleExtra(EXTRA_ALERT_RADIUS, 500.0) ?: 500.0

        val notification = buildNotification("Tracking to $destinationName", "Calculating distance...")
        startForeground(NOTIFICATION_ID, notification)

        trackingManager.startTracking(destLat, destLon)
        return START_STICKY
    }

    private fun handleDistanceUpdate(distanceMeters: Double) {
        val distanceText = if (distanceMeters >= 1000) {
            String.format("%.1f km remaining", distanceMeters / 1000.0)
        } else {
            String.format("%d m remaining", distanceMeters.toInt())
        }

        if (distanceMeters <= alertRadiusMeters && !hasAlarmTriggered) {
            hasAlarmTriggered = true
            alarmController.triggerAlarm(vibrate = true)
            updateNotification("ARRIVING at $destinationName!", "Within alert radius! Tap to dismiss.", isAlarm = true)
        } else if (!hasAlarmTriggered) {
            updateNotification("StopNudge: $destinationName", distanceText, isAlarm = false)
        }
    }

    private fun updateNotification(title: String, text: String, isAlarm: Boolean) {
        val notification = buildNotification(title, text, isAlarm)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String, isAlarm: Boolean = false): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackingForegroundService::class.java).apply {
                action = ACTION_STOP_ALARM
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = if (isAlarm) StopNudgeApp.CHANNEL_ALARM else StopNudgeApp.CHANNEL_TRACKING

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(!isAlarm)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss / Stop", stopIntent)
            .setPriority(if (isAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopAlarmAndService() {
        trackingManager.stopTracking()
        alarmController.stopAlarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarmAndService()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_DEST_NAME = "extra_dest_name"
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LON = "extra_dest_lon"
        const val EXTRA_ALERT_RADIUS = "extra_alert_radius"
        const val ACTION_STOP_ALARM = "com.stopnudge.app.ACTION_STOP_ALARM"
    }
}
