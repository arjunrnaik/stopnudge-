package com.wakeme.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaversineDistanceTest {

    @Test
    fun haversineDistanceMeters_samePoint_returnsZero() {
        val lat = 12.9716
        val lon = 77.5946
        val distance = WakeMeTrackingService.haversineDistanceMeters(lat, lon, lat, lon)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun haversineDistanceMeters_knownBengaluruPoints_accurateDistance() {
        // MG Road Metro: (12.9756, 77.6066)
        // Indiranagar Metro: (12.9784, 77.6433)
        // Known geodesic distance ~ 4.0 km (4000m)
        val mgRoadLat = 12.9756
        val mgRoadLon = 77.6066
        val indiranagarLat = 12.9784
        val indiranagarLon = 77.6433

        val distance = WakeMeTrackingService.haversineDistanceMeters(
            mgRoadLat, mgRoadLon,
            indiranagarLat, indiranagarLon
        )

        // Geodesic distance should be around 4000m (+/- 100m)
        assertTrue("Distance should be approx 4000m, was $distance", distance in 3900.0..4150.0)
    }

    @Test
    fun haversineDistanceMeters_smallOffset_microGeofenceAccurate() {
        // ~100 meters offset in latitude (~0.0009 degrees)
        val lat1 = 12.9716
        val lon1 = 77.5946
        val lat2 = 12.9725
        val lon2 = 77.5946

        val distance = WakeMeTrackingService.haversineDistanceMeters(lat1, lon1, lat2, lon2)
        assertEquals(100.0, distance, 5.0)
    }
}
