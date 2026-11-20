package com.wakeme.app.model

/**
 * Vehicle modes corresponding to the Uber/Rapido-style selection screen.
 * Supports City Bus, Namma Metro, and Direct Road / Auto / Cab.
 */
enum class VehicleMode(
    val title: String,
    val iconEmoji: String,
    val defaultRadiusMeters: Float,
    val alertMode: AlertMode,
    val subtitle: String,
    val alertTag: String
) {
    BUS(
        title = "City Bus",
        iconEmoji = "🚌",
        defaultRadiusMeters = 500f,
        alertMode = AlertMode.WAKEUP_ALARM,
        subtitle = "BMTC, KSRTC or commuter road transit.",
        alertTag = "⏰ Loud Wake-up Siren"
    ),
    METRO(
        title = "Namma Metro",
        iconEmoji = "🚇",
        defaultRadiusMeters = 800f,
        alertMode = AlertMode.WAKEUP_ALARM,
        subtitle = "Purple & Green line rapid transit.",
        alertTag = "⏰ Loud Wake-up Siren"
    ),
    CAR(
        title = "Direct Road / Auto",
        iconEmoji = "🚗",
        defaultRadiusMeters = 1000f,
        alertMode = AlertMode.GENTLE_REMINDER,
        subtitle = "Personal car, Uber, Ola, Auto or direct route.",
        alertTag = "🔔 Gentle Reminder"
    )
}
