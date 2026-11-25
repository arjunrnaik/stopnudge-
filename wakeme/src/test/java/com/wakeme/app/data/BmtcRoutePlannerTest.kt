package com.wakeme.app.data

import com.wakeme.app.data.local.transit.BusRouteEntity
import com.wakeme.app.data.local.transit.BusStopEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class BmtcRoutePlannerTest {

    private fun makeStop(
        id: String,
        name: String,
        lat: Double,
        lon: Double,
        ref: String = "",
        groupId: String = ""
    ) = BusStopEntity(
        id = id,
        name = name,
        nameEn = name,
        nameKn = "",
        latitude = lat,
        longitude = lon,
        mode = "bus",
        metroLines = "",
        operator = "BMTC",
        ref = ref,
        groupId = groupId,
        geometryKind = "point",
        flags = "",
        source = "reclaim",
        osmTimestamp = "",
        lastCheckedDate = "",
        verified = 0,
        directionHint = ""
    )

    private fun buildFixtureIndex(): IndexedBmtcData {
        // Corridor Southbound (500-D): Hebbal -> Tin Factory -> Marathahalli -> Bellandur -> Silk Board
        // Plus opposite-side Northbound pole at Tin Factory (Tin Factory NB) and Feeder 314 from KR Puram -> Tin Factory
        val stops = listOf(
            makeStop("hebbal_sb", "Hebbal", 13.0358, 77.5970, "500-D", "grp_hebbal"),
            makeStop("krp_east", "KR Puram", 13.0010, 77.6950, "314", "grp_krp"),
            makeStop("tf_sb", "Tin Factory", 12.9960, 77.6600, "500-D,314", "grp_tf"),
            makeStop("tf_nb", "Tin Factory", 12.9963, 77.6602, "500-D", "grp_tf"), // Opposite side (~35m away)
            makeStop("mara_sb", "Marathahalli", 12.9592, 77.6974, "500-D", "grp_mara"),
            makeStop("bell_sb", "Bellandur", 12.9279, 77.6762, "500-D", "grp_bell"),
            makeStop("silk_sb", "Central Silk Board", 12.9177, 77.6238, "500-D", "grp_silk")
        )

        val routes = listOf(
            BusRouteEntity(
                id = "r_500d_sb",
                routeNo = "500-D",
                origin = "Hebbal",
                destination = "Central Silk Board",
                stopIds = "hebbal_sb,tf_sb,mara_sb,bell_sb,silk_sb",
                stopCount = 5
            ),
            BusRouteEntity(
                id = "r_500d_nb",
                routeNo = "500-D UP",
                origin = "Central Silk Board",
                destination = "Hebbal",
                stopIds = "silk_sb,bell_sb,mara_sb,tf_nb,hebbal_sb",
                stopCount = 5
            ),
            BusRouteEntity(
                id = "r_314",
                routeNo = "314",
                origin = "KR Puram",
                destination = "Tin Factory",
                stopIds = "krp_east,tf_sb",
                stopCount = 2
            )
        )

        return BmtcRoutePlanner.buildIndex(stops, routes)
    }

    @Test
    fun planBusJourneys_directRoute_picksCorrectOppositePoleDirection() {
        val index = buildFixtureIndex()
        // Intentionally pass the WRONG-side northbound pole ("tf_nb") as preferred stop!
        val wrongPole = index.stopById["tf_nb"]!!
        val options = BmtcRoutePlanner.planBusJourneys(
            origin = GeoPoint(12.9962, 77.6601),
            destination = GeoPoint(12.9281, 77.6763),
            destinationName = "Bellandur Gate",
            index = index,
            preferredBoardingStop = wrongPole
        )

        assertTrue(options.isNotEmpty())
        val top = options.first()
        assertFalse(top.isTransferRoute)
        assertEquals("500-D", top.routeTitle)
        assertTrue(top.plan.isBusJourney)
        assertTrue(top.plan.legs.single().isBusLeg)
        // Must pick tf_sb (southbound) -> Mara -> Bellandur (2 stops), NOT Hebbal direction
        assertEquals("tf_sb", top.plan.boardingStation.id)
        assertEquals("bell_sb", top.plan.exitStation.id)
        assertEquals(2, top.plan.totalStops)
    }

    @Test
    fun planBusJourneys_requiresOneTransfer_buildsTwoLegInterchangePlan() {
        val index = buildFixtureIndex()
        val options = BmtcRoutePlanner.planBusJourneys(
            origin = GeoPoint(13.0012, 77.6952), // Near KR Puram (only 314)
            destination = GeoPoint(12.9178, 77.6240), // Near Silk Board (only 500-D)
            destinationName = "Silk Board",
            index = index
        )

        assertTrue(options.isNotEmpty())
        val xfer = options.firstOrNull { it.isTransferRoute }
        assertTrue("Expected a 1-transfer option", xfer != null)
        val plan = xfer!!.plan
        assertTrue(plan.requiresLineChange)
        assertEquals(2, plan.legs.size)
        assertEquals(listOf("314", "500-D"), plan.legs.map { it.lineName })
        assertEquals("Tin Factory", plan.interchangeStations.single().name)
        assertEquals(4, plan.totalStops) // 1 stop on 314 + 3 stops on 500-D
    }

    @Test
    fun planBusJourneys_exitFarFromPointB_setsStage3WalkDistance() {
        val index = buildFixtureIndex()
        // Destination 900m south-west of Bellandur stop
        val destFarFromStop = GeoPoint(12.9210, 77.6705)
        val options = BmtcRoutePlanner.planBusJourneys(
            origin = GeoPoint(13.0358, 77.5970), // Hebbal
            destination = destFarFromStop,
            destinationName = "EcoSpace Campus",
            index = index
        )

        assertTrue(options.isNotEmpty())
        val plan = options.first().plan
        assertTrue("Expected Stage 3 distance > 50m", plan.exitWalkDistanceMeters > 50f)

        // Verify stop progress evaluator triggers 1 stop before exit on the bus leg
        val leg = plan.legs.single()
        val alertStop = leg.stations[leg.stations.size - 2]
        val progress = MetroRoutePlanner.evaluateMetroLegProgress(
            lat = alertStop.latitude,
            lon = alertStop.longitude,
            leg = leg,
            stopsBeforeAlert = 1,
            minStationIndex = 0
        )
        assertTrue(progress.shouldTriggerStopAlert)
        assertEquals(1, progress.stopsRemaining)
    }

    @Test
    fun planBusJourneys_ranksHighFrequencyRouteFirstAndSlicesEncodedShape() {
        val stops = listOf(
            makeStop("s1", "Origin Stop", 12.9716, 77.5946, "500-D,SPL-1"),
            makeStop("s2", "Mid Stop", 12.9616, 77.6146, "500-D,SPL-1"),
            makeStop("s3", "Dest Stop", 12.9516, 77.6346, "500-D,SPL-1")
        )
        // Standard Google encoded polyline covering s1 (12.9716,77.5946) -> s2 (12.9616,77.6146) -> s3 (12.9516,77.6346)
        val encoded = "oodnAgerxMn}@_|Bn}@_|B"
        val routes = listOf(
            BusRouteEntity(
                id = "rare_1trip",
                routeNo = "SPL-1",
                origin = "Origin Stop",
                destination = "Dest Stop",
                stopIds = "s1,s2,s3",
                stopCount = 3,
                dailyTrips = 1,
                firstBus = "09:00",
                lastBus = "09:00",
                freqLabel = "1 trip/day",
                encodedShape = encoded
            ),
            BusRouteEntity(
                id = "trunk_173trips",
                routeNo = "500-D",
                origin = "Origin Stop",
                destination = "Dest Stop",
                stopIds = "s1,s2,s3",
                stopCount = 3,
                dailyTrips = 173,
                firstBus = "00:05",
                lastBus = "21:40",
                freqLabel = "every ~8 min",
                encodedShape = encoded
            )
        )

        val index = BmtcRoutePlanner.buildIndex(stops, routes)
        val options = BmtcRoutePlanner.planBusJourneys(
            origin = GeoPoint(12.9716, 77.5946),
            destination = GeoPoint(12.9516, 77.6346),
            destinationName = "Dest Stop",
            index = index
        )

        assertEquals(2, options.size)
        val top = options.first()
        assertEquals("500-D", top.routeTitle)
        assertEquals(173, top.dailyTrips)
        assertEquals("every ~8 min • 00:05–21:40", top.frequencyText)
        val sliced = top.legShapePoints[0]
        assertTrue("Expected offline sliced corridor >= 3 points", (sliced?.size ?: 0) >= 3)
    }
}
