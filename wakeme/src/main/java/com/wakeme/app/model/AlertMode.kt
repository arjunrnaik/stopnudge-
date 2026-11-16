package com.wakeme.app.model

/**
 * Supported alert styles for destination reminders.
 */
enum class AlertMode(val title: String, val description: String) {
    WAKEUP_ALARM(
        title = "Wake-up Alarm",
        description = "Loud repeating alarm + vibration + volume override. Best for sleeping commuters."
    ),
    GENTLE_REMINDER(
        title = "Gentle Reminder",
        description = "Single chime + notification heads-up. Best for drivers and cab passengers."
    )
}
