package com.wakeme.app.model

/**
 * Encapsulates a selected destination and user-configured alarm settings.
 */
data class DestinationConfig(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 800f,
    val vehicleMode: VehicleMode = VehicleMode.METRO,
    val alertMode: AlertMode = AlertMode.WAKEUP_ALARM,
    val audioRouting: AudioRouting = AudioRouting.BOTH,
    val reminderNote: String = "",
    val isTransitStop: Boolean = false,
    val transitType: String? = null,
    val vibrate: Boolean = true
)
