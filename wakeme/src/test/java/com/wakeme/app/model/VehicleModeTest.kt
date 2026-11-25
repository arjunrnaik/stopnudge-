package com.wakeme.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleModeTest {

    @Test
    fun carMode_defaultsToGentleReminderAnd1000m() {
        val car = VehicleMode.CAR
        assertEquals("Cab & Auto", car.title)
        assertEquals("🚕", car.iconEmoji)
        assertEquals(1000f, car.defaultRadiusMeters, 0.01f)
        assertEquals(AlertMode.GENTLE_REMINDER, car.alertMode)
        assertTrue(car.isAvailable)
    }

    @Test
    fun metroMode_defaultsToWakeupAlarmAnd800m() {
        val metro = VehicleMode.METRO
        assertEquals("Namma Metro", metro.title)
        assertEquals("🚇", metro.iconEmoji)
        assertEquals(800f, metro.defaultRadiusMeters, 0.01f)
        assertEquals(AlertMode.WAKEUP_ALARM, metro.alertMode)
        assertTrue(metro.isAvailable)
    }

    @Test
    fun multimodalMode_defaultsToWakeupAlarmAnd800m() {
        val mixed = VehicleMode.MULTIMODAL
        assertEquals("Mixed Transit", mixed.title)
        assertEquals("🔀", mixed.iconEmoji)
        assertEquals(800f, mixed.defaultRadiusMeters, 0.01f)
        assertEquals(AlertMode.WAKEUP_ALARM, mixed.alertMode)
        assertTrue(mixed.isAvailable)
    }

    @Test
    fun busMode_defaultsToWakeupAlarmAnd500m_markedFuture() {
        val bus = VehicleMode.BUS
        assertEquals("City Bus", bus.title)
        assertEquals("🚌", bus.iconEmoji)
        assertEquals(500f, bus.defaultRadiusMeters, 0.01f)
        assertEquals(AlertMode.WAKEUP_ALARM, bus.alertMode)
        assertEquals("⏳ In Future", bus.alertTag)
    }

    @Test
    fun destinationConfig_preservesVehicleMode() {
        val config = DestinationConfig(
            name = "Silk Board",
            latitude = 12.9176,
            longitude = 77.6238,
            radiusMeters = 800f,
            vehicleMode = VehicleMode.MULTIMODAL,
            alertMode = AlertMode.WAKEUP_ALARM
        )
        assertEquals(VehicleMode.MULTIMODAL, config.vehicleMode)
        assertEquals("Silk Board", config.name)
        assertNotNull(config.vehicleMode.iconEmoji)
    }
}
