package com.wakeme.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class OffRouteDeviationTest {

    @Test
    fun pointOnSegment_hasZeroDistance() {
        val lat1 = 12.9716
        val lon1 = 77.5946
        val lat2 = 12.9816
        val lon2 = 77.5946

        // Midpoint
        val midLat = 12.9766
        val midLon = 77.5946

        val dist = WakeMeTrackingService.distanceToSegmentMeters(midLat, midLon, lat1, lon1, lat2, lon2)
        assertTrue("Distance should be near zero for point directly on line, was $dist", dist < 1.0)
    }

    @Test
    fun pointDeviated_calculatesAccurateCrossTrackDistance() {
        // Segment running North-South along longitude 77.5946
        val lat1 = 12.9700
        val lon1 = 77.5946
        val lat2 = 12.9800
        val lon2 = 77.5946

        // Point deviated by ~111 meters East (0.001 degrees lon at equator / Bangalore ~108m)
        val devLat = 12.9750
        val devLon = 77.5956

        val dist = WakeMeTrackingService.distanceToSegmentMeters(devLat, devLon, lat1, lon1, lat2, lon2)
        assertTrue("Distance should be approximately 108m, was $dist", dist in 100.0..115.0)
    }

    @Test
    fun polylineDeviation_identifiesClosestSegment() {
        val polyline = listOf(
            GeoPoint(12.9700, 77.5900),
            GeoPoint(12.9700, 77.6000), // Segment 1: Eastbound
            GeoPoint(12.9800, 77.6000)  // Segment 2: Northbound
        )

        // Point near Segment 1
        val nearPoint = GeoPoint(12.9701, 77.5950)
        val distNear = WakeMeTrackingService.distanceToPolylineMeters(nearPoint.latitude, nearPoint.longitude, polyline)
        assertTrue("Should be ~11 meters from segment 1, was $distNear", distNear < 20.0)

        // Point 200m away from the corner
        val farPoint = GeoPoint(12.9720, 77.6020)
        val distFar = WakeMeTrackingService.distanceToPolylineMeters(farPoint.latitude, farPoint.longitude, polyline)
        assertTrue("Should be > 100 meters off route, was $distFar", distFar > 100.0)
    }
}
