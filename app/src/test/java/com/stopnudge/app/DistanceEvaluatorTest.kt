package com.stopnudge.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DistanceEvaluatorTest {

    @Test
    fun testHaversineDistanceBetweenBengaluruStops() {
        // Majestic to Silk Board (~8.36 km direct straight-line / Haversine)
        val majesticLat = 12.975708
        val majesticLon = 77.572876
        val silkBoardLat = 12.916582
        val silkBoardLon = 77.620567

        val distance = computeDistanceMeters(majesticLat, majesticLon, silkBoardLat, silkBoardLon)
        assertTrue("Distance should be around 8.0km to 9.0km", distance in 8000.0..9000.0)
    }

    @Test
    fun testAlertStageTriggerThreshold() {
        val currentDistance = 450.0 // 450 meters from destination
        val stageThreshold = 500 // 500 meter alert stage

        val shouldTrigger = currentDistance <= stageThreshold
        assertTrue("Stage should trigger when user enters within threshold distance", shouldTrigger)
    }

    private fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
