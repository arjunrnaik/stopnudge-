package com.wakeme.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class MultimodalTransitPlannerTest {

    @Test
    fun planJourney_indiranagarToWhitefield_generatesMultiLegTrip() {
        // Origin: Near Indiranagar Metro (12.9783, 77.6386)
        val origin = GeoPoint(12.9785, 77.6390)
        // Destination: Whitefield Kadugodi (12.9957, 77.7579)
        val destination = GeoPoint(12.9960, 77.7580)

        val plan = MultimodalTransitPlanner.planJourney(origin, destination, "Whitefield ITPL")

        assertNotNull(plan)
        assertEquals("Whitefield ITPL", plan.destinationName)
        assertTrue(plan.legs.isNotEmpty())
        assertTrue(plan.totalDurationMinutes > 0)
        assertTrue(plan.summary.contains("🚇"))
    }

    @Test
    fun planJourney_sameStationCatchment_generatesDirectLeg() {
        val origin = GeoPoint(12.9783, 77.6386)
        val destination = GeoPoint(12.9790, 77.6395) // 100m away near same station

        val plan = MultimodalTransitPlanner.planJourney(origin, destination, "Indiranagar 100ft Road")

        assertNotNull(plan)
        assertTrue(plan.isSameStationCatchment)
        assertEquals(1, plan.legs.size)
        assertEquals(MultimodalLegType.WALK, plan.legs[0].type)
    }

    @Test
    fun planJourney_interchangeTrip_identifiesTransferStation() {
        // Origin on Purple Line: Indiranagar (12.9783, 77.6386)
        val origin = GeoPoint(12.9783, 77.6386)
        // Destination on Green Line: Yeshwanthpur (13.0232, 77.5498)
        val destination = GeoPoint(13.0232, 77.5498)

        val plan = MultimodalTransitPlanner.planJourney(origin, destination, "Yeshwanthpur Market")

        assertNotNull(plan)
        assertNotNull(plan.transferStation)
        assertTrue(plan.transferStation!!.name.contains("Majestic"))
        assertTrue(plan.legs.size >= 3)
    }
}
