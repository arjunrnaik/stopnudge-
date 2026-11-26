package com.wakeme.app.alarm

import com.wakeme.app.service.WakeMeTrackingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WakeMeAlarmControllerTest {

    @Test
    fun stopAlert_andCancelSnooze_resetStateSafely() {
        WakeMeAlarmController.cancelSnooze()
        WakeMeAlarmController.stopAlert(null)

        assertFalse(WakeMeAlarmController.isAlertActive())
        assertFalse(WakeMeAlarmController.isSnoozed.value)
    }

    @Test
    fun distanceToSegmentMeters_midpointProjection_accurateOnLongSegment() {
        // Point directly on midpoint of a ~2km North-South segment at Bengaluru latitude
        val dist = WakeMeTrackingService.distanceToSegmentMeters(
            lat = 12.9700,
            lon = 77.6000,
            lat1 = 12.9600,
            lon1 = 77.6000,
            lat2 = 12.9800,
            lon2 = 77.6000
        )
        assertEquals(0.0, dist, 0.5)
    }
}
