package com.stopnudge.app

import com.stopnudge.app.util.GoogleMapsUrlParser
import org.junit.Assert.*
import org.junit.Test

class GoogleMapsUrlParserTest {

    @Test
    fun testParseRawCoordinates() {
        val input = "12.975708, 77.572876"
        val result = GoogleMapsUrlParser.parse(input)
        assertNotNull(result)
        assertEquals(12.975708, result!!.latitude, 0.0001)
        assertEquals(77.572876, result.longitude, 0.0001)
    }

    @Test
    fun testParseAtSignCoordinatesInUrl() {
        val input = "https://www.google.com/maps/place/Majestic/@12.975708,77.572876,17z"
        val result = GoogleMapsUrlParser.parse(input)
        assertNotNull(result)
        assertEquals(12.975708, result!!.latitude, 0.0001)
        assertEquals(77.572876, result.longitude, 0.0001)
        assertEquals("Majestic", result.label)
    }

    @Test
    fun testParseQueryParamCoordinates() {
        val input = "https://maps.google.com/?q=12.916582,77.620567"
        val result = GoogleMapsUrlParser.parse(input)
        assertNotNull(result)
        assertEquals(12.916582, result!!.latitude, 0.0001)
        assertEquals(77.620567, result.longitude, 0.0001)
    }

    @Test
    fun testParseSearchApiCoordinates() {
        val input = "https://www.google.com/maps/search/?api=1&query=12.9352,77.6245"
        val result = GoogleMapsUrlParser.parse(input)
        assertNotNull(result)
        assertEquals(12.9352, result!!.latitude, 0.0001)
        assertEquals(77.6245, result.longitude, 0.0001)
    }

    @Test
    fun testInvalidOrMalformedUrls() {
        assertNull(GoogleMapsUrlParser.parse(""))
        assertNull(GoogleMapsUrlParser.parse("hello world"))
        assertNull(GoogleMapsUrlParser.parse("https://google.com/search?q=weather"))
    }

    @Test
    fun testMalformedPercentEscapedUrlDoesNotCrash() {
        // Link with invalid trailing % or malformed hex escape should not throw IllegalArgumentException
        val malformed = "https://www.google.com/maps/place/Station%2%/@12.975708,77.572876,17z"
        val result = GoogleMapsUrlParser.parse(malformed)
        assertNotNull(result)
        assertEquals(12.975708, result!!.latitude, 0.0001)
    }

    @Test
    fun testOversizedInputRejection() {
        val hugeInput = "a".repeat(5000)
        assertNull(GoogleMapsUrlParser.parse(hugeInput))
    }

    @Test
    fun testBengaluruBoundsCheck() {
        assertTrue(GoogleMapsUrlParser.isWithinBengaluruBounds(12.9716, 77.5946)) // Bengaluru Central
        assertFalse(GoogleMapsUrlParser.isWithinBengaluruBounds(18.9220, 72.8347)) // Mumbai
    }
}
