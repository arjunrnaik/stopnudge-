package com.wakeme.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class ParsedLocation(
    val name: String?,
    val latitude: Double,
    val longitude: Double
)

object GoogleMapsUrlParser {

    private val COORD_PATTERN = Pattern.compile("(-?\\d{1,2}\\.\\d{3,10})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,10})")
    private val AT_COORD_PATTERN = Pattern.compile("@(-?\\d{1,2}\\.\\d{3,10}),(-?\\d{1,3}\\.\\d{3,10})")
    private val QUERY_COORD_PATTERN = Pattern.compile("[?&](?:q|query|ll)=(-?\\d{1,2}\\.\\d{3,10}),(-?\\d{1,3}\\.\\d{3,10})")
    private val URL_PATTERN = Pattern.compile("https?://\\S+")

    /**
     * Parses raw text shared from Google Maps, WhatsApp, or browser.
     * Handles direct lat/lng, full Google Maps URLs, and short goo.gl links.
     */
    suspend fun parseSharedText(text: String): ParsedLocation? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()

        // 1. Check for URL within text
        val urlMatcher = URL_PATTERN.matcher(trimmed)
        var extractedUrl: String? = null
        if (urlMatcher.find()) {
            extractedUrl = urlMatcher.group()
        }

        // Try extracting place title (first line before the URL if available)
        val candidateName = if (extractedUrl != null) {
            val lines = trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            lines.firstOrNull { !it.startsWith("http") && !it.contains("maps.app.goo.gl") }
        } else {
            null
        }

        // 2. Direct regex match on full text or query parameters
        val directMatch = extractCoordinatesFromText(trimmed)
        if (directMatch != null) {
            return@withContext ParsedLocation(
                name = candidateName ?: "Shared Destination",
                latitude = directMatch.first,
                longitude = directMatch.second
            )
        }

        // 3. If there is a short URL, resolve redirect
        if (extractedUrl != null && (extractedUrl.contains("goo.gl") || extractedUrl.contains("maps.app"))) {
            val resolved = resolveRedirect(extractedUrl)
            if (resolved != null) {
                val coords = extractCoordinatesFromText(resolved)
                if (coords != null) {
                    return@withContext ParsedLocation(
                        name = candidateName ?: extractPlaceNameFromUrl(resolved) ?: "Shared Destination",
                        latitude = coords.first,
                        longitude = coords.second
                    )
                }
            }
        }

        return@withContext null
    }

    private fun extractCoordinatesFromText(text: String): Pair<Double, Double>? {
        // Match @lat,lng
        val atMatcher = AT_COORD_PATTERN.matcher(text)
        if (atMatcher.find()) {
            val lat = atMatcher.group(1)?.toDoubleOrNull()
            val lon = atMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // Match ?q=lat,lng or &query=lat,lng
        val queryMatcher = QUERY_COORD_PATTERN.matcher(text)
        if (queryMatcher.find()) {
            val lat = queryMatcher.group(1)?.toDoubleOrNull()
            val lon = queryMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // Match standalone "lat, lon"
        val plainMatcher = COORD_PATTERN.matcher(text)
        if (plainMatcher.find()) {
            val lat = plainMatcher.group(1)?.toDoubleOrNull()
            val lon = plainMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        return null
    }

    private fun resolveRedirect(urlString: String): String? {
        var currentUrl = urlString
        for (i in 0 until 5) {
            try {
                val url = URL(currentUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                }
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location") ?: break
                    currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                } else {
                    break
                }
            } catch (_: Exception) {
                break
            }
        }
        return currentUrl
    }

    private fun extractPlaceNameFromUrl(url: String): String? {
        val marker = "/place/"
        val idx = url.indexOf(marker)
        if (idx != -1) {
            val after = url.substring(idx + marker.length)
            val placeSegment = after.substringBefore("/").substringBefore("?")
            if (placeSegment.isNotEmpty()) {
                return placeSegment.replace("+", " ")
            }
        }
        return null
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }
}
