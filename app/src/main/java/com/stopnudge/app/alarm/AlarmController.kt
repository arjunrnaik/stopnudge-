package com.stopnudge.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmController(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isAlarmPlaying = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    // Failsafe auto-silence after 5 minutes to prevent battery exhaustion and speaker overheating
    private val autoSilenceRunnable = Runnable {
        if (isAlarmPlaying) {
            Log.i("AlarmController", "Auto-silencing alarm after 5 minutes timeout")
            stopAlarm()
        }
    }

    fun triggerAlarm(vibrate: Boolean = true) {
        if (isAlarmPlaying) return
        isAlarmPlaying = true

        requestAudioFocus()

        try {
            // 1. Play sound using USAGE_ALARM (bypasses silent switch)
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmController", "Failed to start alarm MediaPlayer", e)
        }

        // 2. Continuous pulse vibration
        if (vibrate) {
            startVibration()
        }

        // Schedule 5-minute failsafe timeout
        handler.postDelayed(autoSilenceRunnable, 5 * 60 * 1000L)
    }

    fun stopAlarm() {
        handler.removeCallbacks(autoSilenceRunnable)

        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w("AlarmController", "Error releasing MediaPlayer", e)
        }

        stopVibration()
        abandonAudioFocus()
        isAlarmPlaying = false
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { /* maintain alarm */ }
                    .build()

                focusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Log.w("AlarmController", "Could not request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w("AlarmController", "Could not abandon audio focus", e)
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 800, 400, 800, 400, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val effect = VibrationEffect.createWaveform(pattern, 0)
            vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.cancel()
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.cancel()
        }
    }
}
