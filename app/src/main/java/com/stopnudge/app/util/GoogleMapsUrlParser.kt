package com.stopnudge.app.util

import java.util.regex.Pattern

data class ParsedCoordinates(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null
)

object GoogleMapsUrlParser {

    // Matches @12.975708,77.572876 or q=12.975708,77.572876 or query=12.975708,77.572876 or daddr=12.975708,77.572876
    private val COORD_PATTERN = Pattern.compile(
        """(?:@|q=|query=|daddr=|ll=)(-?\d{1,2}\.\d+)[,\s]+(-?\d{1,3}\.\d+)"""
    )

    // Matches raw coordinate input: "12.975708, 77.572876" or "12.975708,77.572876"
    private val RAW_COORD_PATTERN = Pattern.compile(
        """^\s*(-?\d{1,2}\.\d+)[,\s]+(-?\d{1,3}\.\d+)\s*$"""
    )

    fun parse(input: String?): ParsedCoordinates? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()

        // 1. Check raw coordinates
        val rawMatcher = RAW_COORD_PATTERN.matcher(trimmed)
        if (rawMatcher.find()) {
            val lat = rawMatcher.group(1)?.toDoubleOrNull() ?: return null
            val lon = rawMatcher.group(2)?.toDoubleOrNull() ?: return null
            if (isValidCoordinates(lat, lon)) {
                return ParsedCoordinates(lat, lon, "Pinned Location")
            }
        }

        // 2. Check URL coordinate pattern
        val urlMatcher = COORD_PATTERN.matcher(trimmed)
        if (urlMatcher.find()) {
            val lat = urlMatcher.group(1)?.toDoubleOrNull() ?: return null
            val lon = urlMatcher.group(2)?.toDoubleOrNull() ?: return null
            if (isValidCoordinates(lat, lon)) {
                // Attempt to extract place name from URL if present (/place/NAME/...)
                val placeName = extractPlaceName(trimmed)
                return ParsedCoordinates(lat, lon, placeName ?: "Google Maps Destination")
            }
        }

        return null
    }

    private fun extractPlaceName(url: String): String? {
        val placePattern = Pattern.compile("""/place/([^/@]+)""")
        val matcher = placePattern.matcher(url)
        return if (matcher.find()) {
            val raw = matcher.group(1) ?: return null
            java.net.URLDecoder.decode(raw.replace("+", " "), "UTF-8")
        } else {
            null
        }
    }

    fun isValidCoordinates(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }

    fun isWithinBengaluruBounds(lat: Double, lon: Double): Boolean {
        return lat in 12.6..13.4 && lon in 77.3..77.9
    }
}
