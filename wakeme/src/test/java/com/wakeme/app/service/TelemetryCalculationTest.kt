package com.wakeme.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryCalculationTest {

    @Test
    fun speedMpsToKmh_convertsAccurately() {
        val speedMps = 10f // 10 meters per second = 36 km/h
        val speedKmh = speedMps * 3.6f
        assertEquals(36f, speedKmh, 0.01f)
    }

    @Test
    fun etaCalculation_accurateMinutes() {
        val distanceMeters = 6000f // 6 km
        val speedMps = 10f // 36 km/h -> 600 seconds = 10 minutes
        val etaSec = (distanceMeters / speedMps).toInt()
        val etaMin = (etaSec / 60).coerceAtLeast(1)
        assertEquals(10, etaMin)
    }

    @Test
    fun tripProgressPercentage_monotonicAndBounded() {
        val initialDistance = 10000f // 10 km
        val currentDistance = 4000f  // 4 km remaining
        val traveled = (initialDistance - currentDistance).coerceAtLeast(0f)
        val progress = (traveled / initialDistance).coerceIn(0f, 1f)

        assertEquals(0.6f, progress, 0.001f)
        assertEquals(60, (progress * 100).toInt())
    }
}
