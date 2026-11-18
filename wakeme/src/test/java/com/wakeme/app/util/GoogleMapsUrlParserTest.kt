package com.wakeme.app.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsUrlParserTest {

    @Test
    fun parseSharedText_rawCoordinates_returnsLocation() = runBlocking {
        val input = "12.9716, 77.5946"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9716, result!!.latitude, 0.0001)
        assertEquals(77.5946, result.longitude, 0.0001)
    }

    @Test
    fun parseSharedText_atCoordinatesUrl_returnsLocation() = runBlocking {
        val input = "Check out this place: https://www.google.com/maps/place/Cubbon+Park/@12.9763,77.5929,17z/data=..."
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9763, result!!.latitude, 0.0001)
        assertEquals(77.5929, result.longitude, 0.0001)
    }

    @Test
    fun parseSharedText_queryCoordinatesUrl_returnsLocation() = runBlocking {
        val input = "https://www.google.com/maps/search/?api=1&query=12.9279,77.6271"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9279, result!!.latitude, 0.0001)
        assertEquals(77.6271, result.longitude, 0.0001)
    }

    @Test
    fun parseSharedText_urlEncodedQuery_returnsLocation() = runBlocking {
        val input = "https://www.google.com/maps/search/?api=1&query=12.9279%2C77.6271"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9279, result!!.latitude, 0.0001)
        assertEquals(77.6271, result.longitude, 0.0001)
    }

    @Test
    fun parseSharedText_dmsDroppedPin_returnsLocation() = runBlocking {
        val input = "Dropped pin\nnear Bengaluru, Karnataka\n12°58'12.4\"N 77°35'44.2\"E\nhttps://maps.app.goo.gl/xyz"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        // 12 + 58/60 + 12.4/3600 = 12.97011
        // 77 + 35/60 + 44.2/3600 = 77.59561
        assertEquals(12.9701, result!!.latitude, 0.001)
        assertEquals(77.5956, result.longitude, 0.001)
    }

    @Test
    fun parseSharedText_geoUri_returnsLocation() = runBlocking {
        val input = "geo:12.9716,77.5946?q=12.9716,77.5946"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9716, result!!.latitude, 0.0001)
        assertEquals(77.5946, result.longitude, 0.0001)
    }

    @Test
    fun parseSharedText_dataCoordinates_returnsLocation() = runBlocking {
        val input = "https://www.google.com/maps/place/MG+Road/data=!4m2!3m1!1s0x0:0x0!3d12.9755!4d77.6066"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9755, result!!.latitude, 0.0001)
        assertEquals(77.6066, result.longitude, 0.0001)
    }

    @Test
    fun parseSharedText_invalidString_returnsNull() = runBlocking {
        val input = "Just a random message with no location"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNull(result)
    }
}
