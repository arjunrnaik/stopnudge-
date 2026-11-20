package com.wakeme.app.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun parseSharedText_staticmapCenter_returnsLocation() = runBlocking {
        val input = "https://maps.google.com/maps/api/staticmap?center=12.9763%2C77.5929&zoom=17"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9763, result!!.latitude, 0.0001)
        assertEquals(77.5929, result.longitude, 0.0001)
    }

    @Test
    fun parseSharedText_firebaseDynamicLink_returnsLocation() = runBlocking {
        val input = "https://maps.app.goo.gl/?link=https%3A%2F%2Fwww.google.com%2Fmaps%2Fplace%2FIndiranagar%2F%4012.9784%2C77.6408%2C17z&apn=com.google.android.apps.maps"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9784, result!!.latitude, 0.0001)
        assertEquals(77.6408, result.longitude, 0.0001)
    }

    @Test
    fun parseSharedText_intentUrl_returnsLocation() = runBlocking {
        val input = "intent://maps.google.com/maps?q=12.9176,77.6238&m=1#Intent;scheme=https;package=com.google.android.apps.maps;end"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNotNull(result)
        assertEquals(12.9176, result!!.latitude, 0.0001)
        assertEquals(77.6238, result.longitude, 0.0001)
    }

    @Test
    fun extractCleanPlaceName_formatsCorrectly() {
        val raw = "Sai Ladies Fashion - Sunitha Tailor since 2014, 6th Cross Rd, opp. to Neni's Nest appartment, Anugraha Layout, Sathya Layout, B Narayanapura, Mahadevapura, Bengaluru, Karnataka 560016"
        val clean = GoogleMapsUrlParser.extractCleanPlaceName(raw)
        assertEquals("Sai Ladies Fashion - Sunitha Tailor since 2014", clean)
    }

    @Test
    fun generateAddressCandidates_producesProgressiveSubsets() {
        val raw = "Shop, Street, Layout, Area, City, State Pincode"
        val candidates = GoogleMapsUrlParser.generateAddressCandidates(raw)
        assertTrue(candidates.size >= 4)
        assertEquals("Shop, Street, Layout, Area, City, State Pincode", candidates[0])
        assertTrue(candidates.contains("Street, Layout, Area, City, State Pincode"))
        assertTrue(candidates.contains("Area, City, State Pincode"))
    }

    @Test
    fun parseSharedText_invalidString_returnsNull() = runBlocking {
        val input = "Just a random message with no location"
        val result = GoogleMapsUrlParser.parseSharedText(input)
        assertNull(result)
    }
}
