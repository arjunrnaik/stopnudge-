package com.wakeme.app.data

import com.wakeme.app.model.VehicleMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

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

    @Test
    fun evaluateArrivalTrigger_carMode_ignoresFlyoverStraightLineFalseTrigger() {
        // Cab is 450m straight-line away across a divider/flyover, but 2800m by road
        val decision = RoadRoutingRepository.evaluateArrivalTrigger(
            vehicleMode = VehicleMode.CAR,
            targetThresholdMeters = 1000f,
            straightLineMeters = 450f,
            roadDistanceMeters = 2800f,
            hasActiveRoadRoute = true
        )
        assertFalse("Must NOT trigger early across flyover/divider when road distance is 2800m", decision.shouldTrigger)
        assertEquals(2800f, decision.effectiveDistanceMeters, 0.1f)
    }

    @Test
    fun evaluateArrivalTrigger_carMode_triggersWhenRoadDistanceWithinThreshold() {
        val decision = RoadRoutingRepository.evaluateArrivalTrigger(
            vehicleMode = VehicleMode.CAR,
            targetThresholdMeters = 1000f,
            straightLineMeters = 850f,
            roadDistanceMeters = 950f,
            hasActiveRoadRoute = true
        )
        assertTrue("Should trigger once remaining road distance <= 1000m", decision.shouldTrigger)
    }

    @Test
    fun evaluateArrivalTrigger_carMode_triggersOnMicroArrivalGate() {
        // Pin inside gated campus 100m straight-line, road router snapped further around block
        val decision = RoadRoutingRepository.evaluateArrivalTrigger(
            vehicleMode = VehicleMode.CAR,
            targetThresholdMeters = 500f,
            straightLineMeters = 100f,
            roadDistanceMeters = 700f,
            hasActiveRoadRoute = true
        )
        assertTrue("Micro-arrival gate safety net should trigger at <= 120m", decision.shouldTrigger)
    }

    @Test
    fun evaluateArrivalTrigger_carMode_offlineFallbackUsesCurvature() {
        val farOffline = RoadRoutingRepository.evaluateArrivalTrigger(
            vehicleMode = VehicleMode.CAR,
            targetThresholdMeters = 1000f,
            straightLineMeters = 900f,
            roadDistanceMeters = null,
            hasActiveRoadRoute = false
        )
        assertFalse("900m * 1.28 = 1152m > 1000m, should wait offline", farOffline.shouldTrigger)

        val closeOffline = RoadRoutingRepository.evaluateArrivalTrigger(
            vehicleMode = VehicleMode.CAR,
            targetThresholdMeters = 1000f,
            straightLineMeters = 750f,
            roadDistanceMeters = null,
            hasActiveRoadRoute = false
        )
        assertTrue("750m * 1.28 = 960m <= 1000m, should trigger offline", closeOffline.shouldTrigger)
    }

    @Test
    fun evaluateArrivalTrigger_metroMode_usesStationRadius() {
        val decision = RoadRoutingRepository.evaluateArrivalTrigger(
            vehicleMode = VehicleMode.METRO,
            targetThresholdMeters = 800f,
            straightLineMeters = 750f,
            roadDistanceMeters = 2400f,
            hasActiveRoadRoute = true
        )
        assertTrue("Metro should keep station-centric radius trigger", decision.shouldTrigger)
    }

    @Test
    fun computeRemainingRoadDistanceAlongPolyline_preventsJumpingAcrossMedian() {
        // Synthetic divided-highway U-turn loop:
        // Outbound South along lon=77.6000 -> U-turn at bottom -> Return North along lon=77.6003 (~32m east)
        val outboundLatStart = 12.9800
        val uTurnLat = 12.9710 // ~1.0 km South
        val poly = listOf(
            GeoPoint(outboundLatStart, 77.6000),
            GeoPoint(12.9755, 77.6000),
            GeoPoint(uTurnLat, 77.6000),
            GeoPoint(uTurnLat, 77.6003),
            GeoPoint(12.9755, 77.6003),
            GeoPoint(outboundLatStart, 77.6003) // Destination across median from start
        )

        // Cab is just starting on outbound side (near segment 0), 30m west of destination lane
        val progress = RoadRoutingRepository.computeRemainingRoadDistanceAlongPolyline(
            lat = 12.9790,
            lon = 77.6000,
            polyline = poly,
            minSegmentIndex = 0
        )

        assertNotNull(progress)
        assertTrue(
            "Remaining road distance along loop should be > 1700m (not snapped across median!), was ${progress!!.remainingMeters}",
            progress.remainingMeters > 1700f
        )
        assertTrue("Matched segment should stay on outbound side (0 or 1), was ${progress.matchedSegmentIndex}", progress.matchedSegmentIndex <= 1)
    }

    @Test
    fun extractFinalAlertSegment_extractsAccurateTailLength() {
        // Straight Northbound polyline ~2.0 km
        val poly = listOf(
            GeoPoint(12.9600, 77.6000),
            GeoPoint(12.9700, 77.6000),
            GeoPoint(12.9790, 77.6000)
        )
        val tail = RoadRoutingRepository.extractFinalAlertSegment(poly, 500f)
        assertTrue("Alert segment should have at least 2 points", tail.size >= 2)
        val tailLen = RoadRoutingRepository.haversineDistanceMeters(
            tail.first().latitude, tail.first().longitude,
            tail.last().latitude, tail.last().longitude
        )
        assertEquals(500.0, tailLen, 15.0)
    }
}
