package com.wakeme.app.model

/**
 * 3 Core Ways to Travel + Google Maps Multimodal Transit Option:
 * 1. CAB & BOOKING TRANSPORT: Uber, Ola, Rapido, Auto, Taxi or direct car ride.
 * 2. NAMMA METRO: Active BMRCL rapid transit network (Purple, Green, Yellow, Pink).
 * 3. CITY BUS (BMTC): Planned for upcoming future update (BMTC / KSRTC).
 * 4. MULTIMODAL MIXED TRANSIT: Walk + Metro + Cab / Bus (Google Maps style step-by-step route with transfer).
 */
enum class VehicleMode(
    val title: String,
    val iconEmoji: String,
    val defaultRadiusMeters: Float,
    val alertMode: AlertMode,
    val subtitle: String,
    val alertTag: String,
    val isAvailable: Boolean = true
) {
    CAR(
        title = "Cab & Auto",
        iconEmoji = "🚕",
        defaultRadiusMeters = 1000f,
        alertMode = AlertMode.GENTLE_REMINDER,
        subtitle = "Uber, Ola, Rapido, Auto & booking transport.",
        alertTag = "🔔 Gentle Reminder"
    ),
    METRO(
        title = "Namma Metro",
        iconEmoji = "🚇",
        defaultRadiusMeters = 800f,
        alertMode = AlertMode.WAKEUP_ALARM,
        subtitle = "Purple, Green, Yellow & Pink rapid transit.",
        alertTag = "⏰ Loud Wake-up Siren"
    ),
    MULTIMODAL(
        title = "Mixed Transit",
        iconEmoji = "🔀",
        defaultRadiusMeters = 800f,
        alertMode = AlertMode.WAKEUP_ALARM,
        subtitle = "Walk + Metro + Cab (Google Maps style).",
        alertTag = "⏰ Station & Destination Alert"
    ),
    BUS(
        title = "City Bus",
        iconEmoji = "🚌",
        defaultRadiusMeters = 500f,
        alertMode = AlertMode.WAKEUP_ALARM,
        subtitle = "BMTC details coming in future update.",
        alertTag = "⏳ In Future",
        isAvailable = false
    );

    companion object {
        val CAB: VehicleMode get() = CAR
    }
}
