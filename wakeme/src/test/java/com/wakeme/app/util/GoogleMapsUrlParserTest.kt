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
    fun parseSharedText_invalidString_returnsNull() = runBlocking {
        val input = "Just a random message with no location"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNull(result)
    }
}
