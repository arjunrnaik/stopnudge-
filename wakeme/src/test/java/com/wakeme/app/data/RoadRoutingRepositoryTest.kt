package com.wakeme.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadRoutingRepositoryTest {

    @Test
    fun getRoute_returnsValidRouteResultWithPoints() = runBlocking {
        // MG Road to Indiranagar
        val startLat = 12.9756
        val startLon = 77.6066
        val destLat = 12.9784
        val destLon = 77.6433

        val result = RoadRoutingRepository.getRoute(startLat, startLon, destLat, destLon)

        assertNotNull(result)
        // Straight line is ~4000m, road distance with curvature should be >= 4000m
        assertTrue("Distance should be positive and >= 4000m, was ${result.distanceMeters}", result.distanceMeters >= 4000f)
        assertTrue("Duration should be positive, was ${result.durationSeconds}", result.durationSeconds > 0f)
        assertTrue("Should contain at least 2 points (origin & destination), was ${result.points.size}", result.points.size >= 2)
    }

    @Test
    fun getRoute_zeroDistance_handledGracefully() = runBlocking {
        val lat = 12.9716
        val lon = 77.5946

        val result = RoadRoutingRepository.getRoute(lat, lon, lat, lon)

        assertNotNull(result)
        assertTrue("Zero-length trip should have ~0 distance", result.distanceMeters < 100f)
    }
}
