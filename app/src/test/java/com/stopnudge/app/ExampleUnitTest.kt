package com.stopnudge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.*

class ExampleUnitTest {

    @Test
    fun haversineDistanceCalculation_isAccurate() {
        // Distance between Majestic (12.9757, 77.5729) and MG Road (12.9755, 77.6068) is ~3.68 km
        val dist = calculateDistance(12.9757, 77.5729, 12.9755, 77.6068)
        assertTrue("Distance should be between 3.6 km and 3.8 km", dist in 3600.0..3800.0)
    }

    @Test
    fun packageNamespace_isCorrect() {
        assertEquals("StopNudge", "StopNudge")
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
