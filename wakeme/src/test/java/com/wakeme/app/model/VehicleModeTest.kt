package com.wakeme.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VehicleModeTest {

    @Test
    fun carMode_defaultsToGentleReminderAnd1000m() {
        val car = VehicleMode.CAR
        assertEquals("Direct Road / Auto", car.title)
        assertEquals("🚗", car.iconEmoji)
        assertEquals(1000f, car.defaultRadiusMeters, 0.01f)
        assertEquals(AlertMode.GENTLE_REMINDER, car.alertMode)
    }

    @Test
    fun busMode_defaultsToWakeupAlarmAnd500m() {
        val bus = VehicleMode.BUS
        assertEquals("City Bus", bus.title)
        assertEquals("🚌", bus.iconEmoji)
        assertEquals(500f, bus.defaultRadiusMeters, 0.01f)
        assertEquals(AlertMode.WAKEUP_ALARM, bus.alertMode)
    }

    @Test
    fun metroMode_defaultsToWakeupAlarmAnd800m() {
        val metro = VehicleMode.METRO
        assertEquals("Namma Metro", metro.title)
        assertEquals("🚇", metro.iconEmoji)
        assertEquals(800f, metro.defaultRadiusMeters, 0.01f)
        assertEquals(AlertMode.WAKEUP_ALARM, metro.alertMode)
    }

    @Test
    fun destinationConfig_preservesVehicleMode() {
        val config = DestinationConfig(
            name = "Silk Board",
            latitude = 12.9176,
            longitude = 77.6238,
            radiusMeters = 500f,
            vehicleMode = VehicleMode.BUS,
            alertMode = AlertMode.WAKEUP_ALARM
        )
        assertEquals(VehicleMode.BUS, config.vehicleMode)
        assertEquals("Silk Board", config.name)
        assertNotNull(config.vehicleMode.iconEmoji)
    }
}
