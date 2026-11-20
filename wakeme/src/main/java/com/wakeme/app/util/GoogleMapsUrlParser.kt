package com.wakeme.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern

data class ParsedLocation(
    val name: String?,
    val latitude: Double,
    val longitude: Double
)

object GoogleMapsUrlParser {

    private val COORD_PATTERN = Pattern.compile("(-?\\d{1,2}\\.\\d{3,10})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,10})")
    private val AT_COORD_PATTERN = Pattern.compile("(?:@|%40)(-?\\d{1,2}\\.\\d{3,10})(?:,|%2[cC])(-?\\d{1,3}\\.\\d{3,10})", Pattern.CASE_INSENSITIVE)
    private val QUERY_COORD_PATTERN = Pattern.compile("[?&](?:q|query|ll|saddr|daddr|destination)=(-?\\d{1,2}\\.\\d{3,10})(?:,|%2[cC])(-?\\d{1,3}\\.\\d{3,10})", Pattern.CASE_INSENSITIVE)
    private val CENTER_COORD_PATTERN = Pattern.compile("center=(-?\\d{1,2}\\.\\d{3,10})(?:%2[cC]|,)(-?\\d{1,3}\\.\\d{3,10})", Pattern.CASE_INSENSITIVE)
    private val DATA_COORD_PATTERN = Pattern.compile("!3d(-?\\d{1,2}\\.\\d{3,10})!4d(-?\\d{1,3}\\.\\d{3,10})")
    private val GEO_URI_PATTERN = Pattern.compile("geo:(-?\\d{1,2}\\.\\d{3,10})(?:,|%2[cC])(-?\\d{1,3}\\.\\d{3,10})", Pattern.CASE_INSENSITIVE)
    private val DMS_PATTERN = Pattern.compile("(\\d{1,2})°(\\d{1,2})'([\\d.]+)\"([NS])\\s*,?\\s*(\\d{1,3})°(\\d{1,2})'([\\d.]+)\"([EW])", Pattern.CASE_INSENSITIVE)
    private val URL_PATTERN = Pattern.compile("(https?://\\S+|intent://\\S+)")
    private val LINK_PARAM_PATTERN = Pattern.compile("[?&](?:link|url)=([^&\\s]+)", Pattern.CASE_INSENSITIVE)

    /**
     * Parses raw text shared from Google Maps, WhatsApp, or browser.
     * Handles direct lat/lng, DMS coordinates, Google Maps URLs, and short maps.app.goo.gl links.
     */
    suspend fun parseSharedText(text: String): ParsedLocation? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        val decoded = try {
            URLDecoder.decode(trimmed, "UTF-8")
        } catch (_: Exception) {
            trimmed
        }

        // 1. Direct regex match on full text or query parameters (including DMS and geo: URIs)
        val directMatch = extractCoordinatesFromText(trimmed) ?: extractCoordinatesFromText(decoded)
        if (directMatch != null) {
            val candidateName = extractCandidateName(trimmed)
            return@withContext ParsedLocation(
                name = candidateName ?: "Shared Destination",
                latitude = directMatch.first,
                longitude = directMatch.second
            )
        }

        // 2. Check for URL within text
        val urlMatcher = URL_PATTERN.matcher(trimmed)
        var extractedUrl: String? = null
        if (urlMatcher.find()) {
            extractedUrl = urlMatcher.group()
        }

        // 3. If there is a URL, resolve redirects
        if (extractedUrl != null) {
            val candidateName = extractCandidateName(trimmed)
            val resolved = resolveRedirect(extractedUrl)
            if (resolved != null) {
                val resolvedDecoded = try { URLDecoder.decode(resolved, "UTF-8") } catch (_: Exception) { resolved }
                val coords = extractCoordinatesFromText(resolved) ?: extractCoordinatesFromText(resolvedDecoded)
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

    private fun extractCandidateName(text: String): String? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return lines.firstOrNull { line ->
            !line.startsWith("http", ignoreCase = true) &&
            !line.startsWith("intent:", ignoreCase = true) &&
            !line.contains("maps.app.goo.gl", ignoreCase = true) &&
            !line.contains("goo.gl", ignoreCase = true) &&
            !line.contains("Dropped pin", ignoreCase = true) &&
            !DMS_PATTERN.matcher(line).find() &&
            !COORD_PATTERN.matcher(line).find()
        }
    }

    fun extractCoordinatesFromText(text: String): Pair<Double, Double>? {
        // 1. Match @lat,lng (Google Maps standard viewport/pin format, supports @ and %40)
        val atMatcher = AT_COORD_PATTERN.matcher(text)
        if (atMatcher.find()) {
            val lat = atMatcher.group(1)?.toDoubleOrNull()
            val lon = atMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // 2. Match center=lat,lng (Google Maps staticmap and og:image metadata)
        val centerMatcher = CENTER_COORD_PATTERN.matcher(text)
        if (centerMatcher.find()) {
            val lat = centerMatcher.group(1)?.toDoubleOrNull()
            val lon = centerMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // 3. Match !3d{lat}!4d{lng} (Google Maps place data parameter)
        val dataMatcher = DATA_COORD_PATTERN.matcher(text)
        if (dataMatcher.find()) {
            val lat = dataMatcher.group(1)?.toDoubleOrNull()
            val lon = dataMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // 4. Match ?q=lat,lng or &query= or &ll= or &daddr= or &destination=
        val queryMatcher = QUERY_COORD_PATTERN.matcher(text)
        if (queryMatcher.find()) {
            val lat = queryMatcher.group(1)?.toDoubleOrNull()
            val lon = queryMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // 5. Match geo:lat,lng URI
        val geoMatcher = GEO_URI_PATTERN.matcher(text)
        if (geoMatcher.find()) {
            val lat = geoMatcher.group(1)?.toDoubleOrNull()
            val lon = geoMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // 6. Match DMS format (e.g., 12°58'12.4"N 77°35'44.2"E or 12°58'12.4"N, 77°35'44.2"E)
        val dmsMatcher = DMS_PATTERN.matcher(text)
        if (dmsMatcher.find()) {
            try {
                val latDeg = dmsMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                val latMin = dmsMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                val latSec = dmsMatcher.group(3)?.toDoubleOrNull() ?: 0.0
                val latDir = dmsMatcher.group(4)?.uppercase() ?: "N"
                var lat = latDeg + (latMin / 60.0) + (latSec / 3600.0)
                if (latDir == "S") lat = -lat

                val lonDeg = dmsMatcher.group(5)?.toDoubleOrNull() ?: 0.0
                val lonMin = dmsMatcher.group(6)?.toDoubleOrNull() ?: 0.0
                val lonSec = dmsMatcher.group(7)?.toDoubleOrNull() ?: 0.0
                val lonDir = dmsMatcher.group(8)?.uppercase() ?: "E"
                var lon = lonDeg + (lonMin / 60.0) + (lonSec / 3600.0)
                if (lonDir == "W") lon = -lon

                if (isValidCoordinate(lat, lon)) {
                    return Pair(lat, lon)
                }
            } catch (_: Exception) {}
        }

        // 7. Match plain decimal "lat, lon"
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
        for (i in 0 until 6) {
            try {
                // If it's an intent URL, extract the real HTTPS URL
                if (currentUrl.startsWith("intent://", ignoreCase = true)) {
                    currentUrl = currentUrl.replaceFirst("intent://", "https://", ignoreCase = true).substringBefore("#Intent")
                }

                // If URL has link= or url= parameter (Firebase Dynamic Link), extract and decode it
                val linkMatcher = LINK_PARAM_PATTERN.matcher(currentUrl)
                if (linkMatcher.find()) {
                    val rawParam = linkMatcher.group(1)
                    if (!rawParam.isNullOrBlank()) {
                        val inner = try { URLDecoder.decode(rawParam, "UTF-8") } catch (_: Exception) { rawParam }
                        val innerCoords = extractCoordinatesFromText(inner)
                        if (innerCoords != null) {
                            return inner
                        }
                        currentUrl = inner
                    }
                }

                val url = URL(currentUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    // Use a Desktop Chrome User-Agent so Google sends clean canonical redirects instead of intent:// schemes
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                }
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location") ?: break
                    connection.disconnect()

                    val targetLocation = if (location.startsWith("intent://", ignoreCase = true)) {
                        location.replaceFirst("intent://", "https://", ignoreCase = true).substringBefore("#Intent")
                    } else if (location.startsWith("http")) {
                        location
                    } else {
                        URL(url, location).toString()
                    }

                    // Check if location header already has coordinates
                    val coords = extractCoordinatesFromText(targetLocation) ?: extractCoordinatesFromText(
                        try { URLDecoder.decode(targetLocation, "UTF-8") } catch (_: Exception) { targetLocation }
                    )
                    if (coords != null) {
                        return targetLocation
                    }
                    currentUrl = targetLocation
                } else if (responseCode == 200) {
                    // Read up to 32KB of HTML to find center= or staticmap or metadata
                    val preview = try {
                        connection.inputStream.bufferedReader().use { reader ->
                            val buf = CharArray(32768)
                            val r = reader.read(buf, 0, 32768)
                            if (r > 0) String(buf, 0, r) else ""
                        }
                    } catch (_: Exception) { "" }
                    connection.disconnect()

                    val coords = extractCoordinatesFromText(preview)
                    if (coords != null) {
                        return "$currentUrl?q=${coords.first},${coords.second}"
                    }
                    val urlInHtml = URL_PATTERN.matcher(preview).let { if (it.find()) it.group() else null }
                    if (urlInHtml != null) {
                        val coordsInHtmlUrl = extractCoordinatesFromText(urlInHtml)
                        if (coordsInHtmlUrl != null) {
                            return "$currentUrl?q=${coordsInHtmlUrl.first},${coordsInHtmlUrl.second}"
                        }
                    }
                    break
                } else {
                    connection.disconnect()
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
                val cleaned = placeSegment.replace("+", " ")
                return try { URLDecoder.decode(cleaned, "UTF-8") } catch (_: Exception) { cleaned }
            }
        }
        return null
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }
}
