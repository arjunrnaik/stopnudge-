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
        assertTrue("Fastest route should have isFastest=true", result.fastestRoute.isFastest)

        // If alternatives exist, verify fastest route has shortest duration (earliest ETA)
        for (alt in result.alternativeRoutes) {
            assertTrue(
                "Fastest route duration (${result.fastestRoute.durationSeconds}s) must be <= alternative duration (${alt.durationSeconds}s)",
                result.fastestRoute.durationSeconds <= alt.durationSeconds
            )
        }
    }

    @Test
    fun getRoute_zeroDistance_handledGracefully() = runBlocking {
        val lat = 12.9716
        val lon = 77.5946

        val result = RoadRoutingRepository.getRoute(lat, lon, lat, lon)

        assertNotNull(result)
        assertTrue("Zero-length trip should have ~0 distance", result.distanceMeters < 100f)
    }

    @Test
    fun findNearestMetroStationByRoad_returnsValidRoadResult() = runBlocking {
        // Indiranagar 100ft Road
        val origin = org.osmdroid.util.GeoPoint(12.9719, 77.6412)

        val result = RoadRoutingRepository.findNearestMetroStationByRoad(origin)

        assertNotNull(result)
        assertNotNull(result.station)
        assertTrue("Road distance should be positive, was ${result.roadDistanceMeters}", result.roadDistanceMeters > 0f)
        assertTrue("Duration should be positive, was ${result.durationSeconds}", result.durationSeconds > 0f)
        assertTrue("Should return route points", result.routePoints.isNotEmpty())
    }

    @Test
    fun haversineDistanceMeters_calculatesCorrectDistance() {
        // Majestic to Vidhana Soudha (~2.15 km)
        val d = RoadRoutingRepository.haversineDistanceMeters(12.9757, 77.5728, 12.9798, 77.5925)
        assertTrue("Expected ~2150m, got $d", d in 2000.0..2300.0)
    }
}
