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

class TrackingForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destName = intent?.getStringExtra(EXTRA_DEST_NAME) ?: "Destination"
        val notification = buildTrackingNotification(destName, "Tracking distance...")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun buildTrackingNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, StopNudgeApp.CHANNEL_TRACKING)
            .setContentTitle("StopNudge: $title")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_DEST_NAME = "extra_dest_name"
    }
}
