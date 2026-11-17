package com.wakeme.app.model

/**
 * Vehicle modes corresponding to the Uber/Rapido-style selection screen.
 * Currently supports Car (Gentle Reminder) and Bus (Wake-up Alarm).
 */
enum class VehicleMode(
    val title: String,
    val iconEmoji: String,
    val defaultRadiusMeters: Float,
    val alertMode: AlertMode,
    val subtitle: String,
    val alertTag: String
) {
    CAR(
        title = "Car / Cab",
        iconEmoji = "🚗",
        defaultRadiusMeters = 1000f,
        alertMode = AlertMode.GENTLE_REMINDER,
        subtitle = "Personal car, Uber, Ola or auto-rickshaw.",
        alertTag = "🔔 Gentle Reminder"
    ),
    BUS(
        title = "City Bus",
        iconEmoji = "🚌",
        defaultRadiusMeters = 500f,
        alertMode = AlertMode.WAKEUP_ALARM,
        subtitle = "BMTC, KSRTC or commuter transit.",
        alertTag = "⏰ Loud Wake-up Siren"
    )
}
