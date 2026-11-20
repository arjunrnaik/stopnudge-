package com.wakeme.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.wakeme.app.model.AlertMode
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.model.DestinationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object WakeMeAlarmController {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPlaying = false
    private var lastConfig: DestinationConfig? = null
    private var snoozeJob: Job? = null

    private val _isSnoozed = MutableStateFlow(false)
    val isSnoozed: StateFlow<Boolean> = _isSnoozed.asStateFlow()

    @Synchronized
    fun triggerAlert(context: Context, config: DestinationConfig) {
        if (isPlaying) return
        isPlaying = true
        lastConfig = config
        _isSnoozed.value = false
        snoozeJob?.cancel()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // Acquire WakeLock to keep CPU awake during alarm
        try {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeMe::AlarmWakeLock").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes auto-timeout max
            }
        } catch (_: Exception) {}

        // Configure and trigger Vibrator if enabled
        if (config.vibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            // Start Vibration Pattern
            try {
                if (config.alertMode == AlertMode.WAKEUP_ALARM) {
                    val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = VibrationEffect.createWaveform(pattern, 0) // Repeat from index 0
                        vibrator?.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, 0)
                    }
                } else {
                    // Gentle reminder: single short vibration pulse
                    val pattern = longArrayOf(0, 350)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = VibrationEffect.createWaveform(pattern, -1) // No repeat
                        vibrator?.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, -1)
                    }
                }
            } catch (_: Exception) {}
        }

        // Select Ringtone URI
        val soundUri: Uri = if (config.alertMode == AlertMode.WAKEUP_ALARM) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        // Configure Audio Stream & Playback
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, soundUri)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(
                        if (config.alertMode == AlertMode.WAKEUP_ALARM)
                            AudioAttributes.USAGE_ALARM
                        else
                            AudioAttributes.USAGE_NOTIFICATION
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)

                isLooping = (config.alertMode == AlertMode.WAKEUP_ALARM)

                setOnCompletionListener {
                    if (config.alertMode == AlertMode.GENTLE_REMINDER) {
                        stopAlert(context)
                    }
                }

                // Handle Audio Routing Preference
                when (config.audioRouting) {
                    AudioRouting.SPEAKER_ONLY -> {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                    AudioRouting.HEADPHONES_ONLY -> {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = false
                    }
                    AudioRouting.BOTH -> {
                        // Default system routing
                    }
                }

                prepare()
                start()
            }
        } catch (_: Exception) {
            // Fallback: if MediaPlayer fails, ensure vibration remains active
        }
    }

    /**
     * Pauses alert sound/vibration for specified duration (default 2 minutes),
     * then automatically sounds the alert again.
     */
    fun snoozeAlert(context: Context, durationMs: Long = 120_000L, onSnoozeFired: (() -> Unit)? = null) {
        val configToResume = lastConfig
        stopAlert(context)
        _isSnoozed.value = true

        snoozeJob = CoroutineScope(Dispatchers.Main).launch {
            delay(durationMs)
            _isSnoozed.value = false
            if (configToResume != null) {
                triggerAlert(context, configToResume)
                onSnoozeFired?.invoke()
            }
        }
    }

    @Synchronized
    fun stopAlert(context: Context? = null) {
        isPlaying = false

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    fun cancelSnooze() {
        snoozeJob?.cancel()
        snoozeJob = null
        _isSnoozed.value = false
    }

    fun isAlertActive(): Boolean = isPlaying
}
