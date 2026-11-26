package com.wakeme.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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
import kotlinx.coroutines.SupervisorJob
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
    private val alarmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

            // Start Vibration Pattern (minSdk >= 29, VibrationEffect always supported)
            try {
                if (config.alertMode == AlertMode.WAKEUP_ALARM) {
                    val pattern = longArrayOf(0, 1000, 500, 1000, 500)
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    val pattern = longArrayOf(0, 350)
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val speaker = audioManager.availableCommunicationDevices
                                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                            if (speaker != null) {
                                audioManager.setCommunicationDevice(speaker)
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = true
                        }
                    }
                    AudioRouting.HEADPHONES_ONLY -> {
                        val hasHeadset = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (hasHeadset) {
                                val headset = audioManager.availableCommunicationDevices.firstOrNull {
                                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                                }
                                if (headset != null) audioManager.setCommunicationDevice(headset)
                            } else {
                                audioManager.clearCommunicationDevice()
                            }
                        } else if (hasHeadset) {
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = false
                        }
                    }
                    AudioRouting.BOTH -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            audioManager.clearCommunicationDevice()
                        }
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
        val appContext = context.applicationContext
        val configToResume = lastConfig
        stopAlert(appContext)
        snoozeJob?.cancel()
        _isSnoozed.value = true

        snoozeJob = alarmScope.launch {
            delay(durationMs)
            _isSnoozed.value = false
            if (configToResume != null) {
                triggerAlert(appContext, configToResume)
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
