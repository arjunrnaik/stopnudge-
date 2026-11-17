package com.wakeme.app.ui

/**
 * Screen destinations in WakeMe's 3-step commuter flow.
 */
enum class WakeMeScreen {
    /** Screen 1: "Where are you heading?" Search bar, map preview, and 1-tap favorites. */
    SEARCH_LANDING,

    /** Screen 2: Car vs. Bus mode selection, geofence radius chips, and deboard note. */
    MODE_SELECTION,

    /** Screen 3: Active GPS satellite tracking HUD with live distance countdown. */
    ACTIVE_TRIP
}
