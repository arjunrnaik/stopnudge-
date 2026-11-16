package com.wakeme.app.model

/**
 * Audio output routing options for the alert audio stream.
 */
enum class AudioRouting(val label: String) {
    BOTH("Speaker & Headphones"),
    HEADPHONES_ONLY("Headphones Only"),
    SPEAKER_ONLY("Speaker Only")
}
