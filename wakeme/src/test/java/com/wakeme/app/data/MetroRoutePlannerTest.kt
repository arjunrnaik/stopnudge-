package com.wakeme.app.data

import com.wakeme.app.data.local.transit.NammaMetroNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroRoutePlannerTest {

    private fun station(nameContains: String): NammaMetroNetwork.ActiveStation {
        return NammaMetroNetwork.ACTIVE_STATIONS.first {
            it.name.contains(nameContains, ignoreCase = true)
        }
    }

    @Test
    fun lineStations_coverAllActiveLinesInOrder() {
        val lines = MetroRoutePlanner.lineStations
        assertEquals(37, lines["Purple"]?.size)
        assertEquals(32, lines["Green"]?.size)
        assertEquals(16, lines["Yellow"]?.size)
        assertEquals(6, lines["Pink"]?.size)
    }

    @Test
    fun planJourney_sameLine_indiranagarToMajestic() {
        val plan = MetroRoutePlanner.planJourney(
            boardingStation = station("Indiranagar"),
            exitStation = station("Majestic")
        )

        assertFalse(plan.requiresLineChange)
        assertEquals(1, plan.legs.size)
        assertEquals("Purple", plan.legs[0].lineName)
        assertEquals(7, plan.totalStops)
        assertEquals("Indiranagar", plan.legs[0].boardingStation.name)
        assertEquals("Nadaprabhu Kempegowda Majestic", plan.legs[0].alightingStation.name)
    }

    @Test
    fun planJourney_oneInterchange_krPuraToJalahalli() {
        val plan = MetroRoutePlanner.planJourney(
            boardingStation = station("KR Pura"),
            exitStation = station("Jalahalli")
        )

        assertTrue(plan.requiresLineChange)
        assertEquals(2, plan.legs.size)
        assertEquals("Purple", plan.legs[0].lineName)
        assertEquals("Green", plan.legs[1].lineName)
        assertEquals(1, plan.interchangeStations.size)
        assertEquals("Nadaprabhu Kempegowda Majestic", plan.interchangeStations[0].name)
        assertEquals(11, plan.legs[0].stopsCount)
        assertEquals(11, plan.legs[1].stopsCount)
        assertEquals(22, plan.totalStops)
        assertTrue(plan.legs[1].directionLabel.contains("Madavara", ignoreCase = true))
    }

    @Test
    fun planJourney_twoInterchanges_whitefieldToElectronicCity() {
        val plan = MetroRoutePlanner.planJourney(
            boardingStation = station("Whitefield"),
            exitStation = station("Electronic City")
        )

        assertTrue(plan.requiresLineChange)
        assertEquals(3, plan.legs.size)
        assertEquals(listOf("Purple", "Green", "Yellow"), plan.legs.map { it.lineName })
        assertEquals(2, plan.interchangeStations.size)
        assertTrue(plan.interchangeStations[0].name.contains("Majestic", ignoreCase = true))
        assertTrue(plan.interchangeStations[1].name.contains("RV Road", ignoreCase = true))
    }

    @Test
    fun planJourney_pinkToYellow_usesJayadevaInterchange() {
        val plan = MetroRoutePlanner.planJourney(
            boardingStation = station("IIM Bangalore"),
            exitStation = station("BTM Layout")
        )

        assertTrue(plan.requiresLineChange)
        assertEquals(listOf("Pink", "Yellow"), plan.legs.map { it.lineName })
        assertEquals("Jayadeva Hospital", plan.interchangeStations.single().name)
    }

    @Test
    fun evaluateMetroLegProgress_triggersAtChosenStopsBeforeAnd50mOption() {
        val plan = MetroRoutePlanner.planJourney(
            boardingStation = station("Majestic"),
            exitStation = station("Jalahalli")
        )
        val greenLeg = plan.legs.single()
        val yeshwanthpur = station("Yeshwanthpur")
        val peenyaIndustry = station("Peenya Industry")
        val peenya = station("Peenya")
        val jalahalli = station("Jalahalli")

        // 1 stop before Jalahalli -> alert stop is Peenya Industry
        val farProgress = MetroRoutePlanner.evaluateMetroLegProgress(
            lat = yeshwanthpur.latitude,
            lon = yeshwanthpur.longitude,
            leg = greenLeg,
            stopsBeforeAlert = 1,
            minStationIndex = 0
        )
        assertEquals("Peenya Industry", farProgress.alertStopStation.name)
        assertFalse("Should not trigger back at Yeshwanthpur", farProgress.shouldTriggerStopAlert)

        val oneStopProgress = MetroRoutePlanner.evaluateMetroLegProgress(
            lat = peenyaIndustry.latitude,
            lon = peenyaIndustry.longitude,
            leg = greenLeg,
            stopsBeforeAlert = 1,
            minStationIndex = farProgress.currentStationIndex
        )
        assertEquals(1, oneStopProgress.stopsRemaining)
        assertTrue("Should trigger at 1 stop before (Peenya Industry)", oneStopProgress.shouldTriggerStopAlert)

        // 2 stops before Jalahalli -> alert stop is Peenya
        val twoStopAlert = plan.getAlertStopForLeg(0, 2)
        assertNotNull(twoStopAlert)
        assertEquals("Peenya", twoStopAlert!!.name)
        assertEquals(peenya.id, twoStopAlert.id)

        // 0 stops before -> strict 50m check at Jalahalli
        val ninetyMetersSouth = MetroRoutePlanner.evaluateMetroLegProgress(
            lat = jalahalli.latitude - 0.0008, // ~89m away
            lon = jalahalli.longitude,
            leg = greenLeg,
            stopsBeforeAlert = 0,
            minStationIndex = oneStopProgress.currentStationIndex
        )
        assertFalse("50m mode must NOT trigger at ~89m", ninetyMetersSouth.shouldTriggerStopAlert)

        val thirtyMetersSouth = MetroRoutePlanner.evaluateMetroLegProgress(
            lat = jalahalli.latitude - 0.00025, // ~28m away
            lon = jalahalli.longitude,
            leg = greenLeg,
            stopsBeforeAlert = 0,
            minStationIndex = oneStopProgress.currentStationIndex
        )
        assertTrue("50m mode must trigger within 50m", thirtyMetersSouth.shouldTriggerStopAlert)
    }

    @Test
    fun evaluateMetroLegProgress_singleStopTrip_doesNotFireImmediatelyAtBoardingStation() {
        val peenyaIndustry = station("Peenya Industry")
        val jalahalli = station("Jalahalli")
        val plan = MetroRoutePlanner.planJourney(
            boardingStation = peenyaIndustry,
            exitStation = jalahalli
        )
        val leg = plan.legs.single()

        val atBoarding = MetroRoutePlanner.evaluateMetroLegProgress(
            lat = peenyaIndustry.latitude,
            lon = peenyaIndustry.longitude,
            leg = leg,
            stopsBeforeAlert = 1,
            minStationIndex = 0
        )
        assertFalse("Single-stop trip should wait until approaching destination, not fire at boarding", atBoarding.shouldTriggerStopAlert)

        val approachingDest = MetroRoutePlanner.evaluateMetroLegProgress(
            lat = jalahalli.latitude - 0.001,
            lon = jalahalli.longitude,
            leg = leg,
            stopsBeforeAlert = 1,
            minStationIndex = 0
        )
        assertTrue("Single-stop trip should fire as train approaches exit station", approachingDest.shouldTriggerStopAlert)
    }
}
