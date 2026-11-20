package com.wakeme.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

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

    private val GOOGLE_DIRECT_IPS = listOf(
        "142.251.221.110",
        "142.250.182.78",
        "172.217.166.46",
        "216.58.200.174",
        "142.250.190.46"
    )

    /**
     * Parses raw text shared from Google Maps, WhatsApp, or browser.
     * Handles:
     * 1. Direct lat/lng coordinates and DMS
     * 2. URLs with embedded coordinates
     * 3. Google Maps short links (maps.app.goo.gl) with ISP DNS poisoning bypass
     * 4. Place URLs with address names via progressive geocoding
     */
    suspend fun parseSharedText(text: String): ParsedLocation? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        val decoded = try {
            URLDecoder.decode(trimmed, "UTF-8")
        } catch (_: Exception) {
            trimmed
        }

        // 1. Direct regex match on full text (including DMS and geo: URIs)
        val directMatch = extractCoordinatesFromText(trimmed) ?: extractCoordinatesFromText(decoded)
        if (directMatch != null) {
            val candidateName = extractCandidateName(trimmed)
            return@withContext ParsedLocation(
                name = candidateName ?: "Shared Destination",
                latitude = directMatch.first,
                longitude = directMatch.second
            )
        }

        // 2. Extract URL from text if present
        val urlMatcher = URL_PATTERN.matcher(trimmed)
        var extractedUrl: String? = null
        if (urlMatcher.find()) {
            extractedUrl = urlMatcher.group()
        }

        val candidateName = extractCandidateName(trimmed)

        // 3. If there is a URL, resolve redirects
        if (extractedUrl != null) {
            val resolved = resolveRedirect(extractedUrl)
            if (resolved != null) {
                val resolvedDecoded = try { URLDecoder.decode(resolved, "UTF-8") } catch (_: Exception) { resolved }

                // Check for coordinates in the resolved URL or decoded URL
                val coords = extractCoordinatesFromText(resolved) ?: extractCoordinatesFromText(resolvedDecoded)
                if (coords != null) {
                    val placeName = extractPlaceNameFromUrl(resolved)
                    val cleanName = candidateName ?: (if (placeName != null) extractCleanPlaceName(placeName) else null) ?: "Shared Destination"
                    return@withContext ParsedLocation(
                        name = cleanName,
                        latitude = coords.first,
                        longitude = coords.second
                    )
                }

                // If coordinates weren't in URL, check if URL points to a named place (/place/<address>)
                val placeString = extractPlaceNameFromUrl(resolved) ?: candidateName
                if (!placeString.isNullOrBlank()) {
                    val cleanName = candidateName ?: extractCleanPlaceName(placeString)
                    val geocoded = geocodeAddressProgressively(placeString)
                    if (geocoded != null) {
                        return@withContext ParsedLocation(
                            name = cleanName,
                            latitude = geocoded.first,
                            longitude = geocoded.second
                        )
                    }
                }
            }
        }

        // 4. If no URL or URL lacked coordinates, try geocoding candidate name from text
        if (!candidateName.isNullOrBlank()) {
            val geocoded = geocodeAddressProgressively(candidateName)
            if (geocoded != null) {
                return@withContext ParsedLocation(
                    name = extractCleanPlaceName(candidateName),
                    latitude = geocoded.first,
                    longitude = geocoded.second
                )
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

    fun extractCleanPlaceName(rawPlace: String): String {
        val firstPart = rawPlace.substringBefore(",").trim()
        val cleaned = if (firstPart.isNotBlank()) firstPart else rawPlace.take(50).trim()
        return cleaned.replace("+", " ").trim()
    }

    fun extractCoordinatesFromText(text: String): Pair<Double, Double>? {
        // 1. Match @lat,lng (Google Maps standard viewport/pin format)
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

        // 6. Match DMS format (e.g., 12°58'12.4"N 77°35'44.2"E)
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
                // If intent URL, convert to HTTPS
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
                val host = url.host.lowercase()

                // If this is a Google Maps short link domain, attempt direct socket resolution
                // in case the local ISP poisons DNS (e.g. Jio resolving maps.app.goo.gl to 49.44.79.236)
                if (host.contains("maps.app.goo.gl") || host.contains("goo.gl")) {
                    val directLocation = resolveGoogleShortlinkDirect(currentUrl)
                    if (!directLocation.isNullOrBlank()) {
                        val coords = extractCoordinatesFromText(directLocation)
                        if (coords != null) return directLocation
                        currentUrl = directLocation
                        continue
                    }
                }

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
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

                    val coords = extractCoordinatesFromText(targetLocation) ?: extractCoordinatesFromText(
                        try { URLDecoder.decode(targetLocation, "UTF-8") } catch (_: Exception) { targetLocation }
                    )
                    if (coords != null) {
                        return targetLocation
                    }
                    currentUrl = targetLocation
                } else if (responseCode == 200) {
                    // Read HTML to check for staticmap, center=, or metadata
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
                    break
                } else {
                    connection.disconnect()
                    break
                }
            } catch (_: Exception) {
                // If connection failed on short link, try socket fallback
                if (currentUrl.contains("maps.app.goo.gl") || currentUrl.contains("goo.gl")) {
                    val direct = resolveGoogleShortlinkDirect(currentUrl)
                    if (direct != null) {
                        currentUrl = direct
                        continue
                    }
                }
                break
            }
        }
        return currentUrl
    }

    /**
     * Connects directly to Google frontends using SNI, bypassing ISP-level DNS poisoning
     * (such as Indian ISPs resolving maps.app.goo.gl to 49.44.79.236).
     */
    private fun resolveGoogleShortlinkDirect(urlString: String): String? {
        val parsed = try { URL(urlString) } catch (_: Exception) { return null }
        val host = parsed.host
        val path = if (parsed.query != null) "${parsed.path}?${parsed.query}" else parsed.path

        val dohIps = fetchIpsFromGoogleDoh(host)
        val ipsToTry = (dohIps + GOOGLE_DIRECT_IPS).distinct()

        for (ip in ipsToTry) {
            try {
                val rawSocket = Socket()
                rawSocket.connect(InetSocketAddress(ip, 443), 3500)
                rawSocket.soTimeout = 4000

                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = factory.createSocket(rawSocket, host, 443, true) as SSLSocket

                val params = sslSocket.sslParameters
                params.serverNames = listOf(SNIHostName(host))
                sslSocket.sslParameters = params
                sslSocket.startHandshake()

                val request = "GET $path HTTP/1.1\r\n" +
                        "Host: $host\r\n" +
                        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n" +
                        "Accept: text/html,*/*\r\n" +
                        "Connection: close\r\n\r\n"

                sslSocket.outputStream.write(request.toByteArray(Charsets.UTF_8))
                sslSocket.outputStream.flush()

                val reader = BufferedReader(InputStreamReader(sslSocket.inputStream, Charsets.UTF_8))
                var line: String?
                var location: String? = null
                var statusCode = 0

                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    if (l.isEmpty()) break
                    if (l.startsWith("HTTP/1.")) {
                        val parts = l.split(" ")
                        if (parts.size > 1) {
                            statusCode = parts[1].toIntOrNull() ?: 0
                        }
                    }
                    if (l.regionMatches(0, "Location: ", 0, 10, ignoreCase = true)) {
                        location = l.substring(10).trim()
                    }
                }
                sslSocket.close()

                if (statusCode in 300..399 && !location.isNullOrBlank()) {
                    return if (location.startsWith("http")) location else URL(parsed, location).toString()
                }
            } catch (_: Exception) {
                // Continue to next IP
            }
        }
        return null
    }

    private fun fetchIpsFromGoogleDoh(hostname: String): List<String> {
        return try {
            val encoded = URLEncoder.encode(hostname, "UTF-8")
            val url = URL("https://dns.google/resolve?name=$encoded&type=A")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "WakeMe-App/2.0")
            }
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                val answers = obj.optJSONArray("Answer")
                if (answers != null) {
                    val ips = mutableListOf<String>()
                    for (i in 0 until answers.length()) {
                        val ans = answers.getJSONObject(i)
                        val type = ans.optInt("type", 0)
                        val data = ans.optString("data", "")
                        if (type == 1 && data.isNotBlank() && !data.startsWith("49.44.79.")) {
                            ips.add(data)
                        }
                    }
                    return ips
                }
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves an address or place description progressively using Nominatim and Photon.
     * E.g. "Sai Ladies Fashion..., 6th Cross, Sathya Layout, B Narayanapura, Mahadevapura, Bengaluru"
     * tries dropping front elements until finding the neighborhood in East Bengaluru.
     */
    suspend fun geocodeAddressProgressively(rawAddress: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val candidates = generateAddressCandidates(rawAddress)
        for (query in candidates) {
            val nominatimCoords = queryNominatim(query)
            if (nominatimCoords != null) {
                return@withContext nominatimCoords
            }
            val photonCoords = queryPhoton(query)
            if (photonCoords != null) {
                return@withContext photonCoords
            }
        }
        null
    }

    fun generateAddressCandidates(rawAddress: String): List<String> {
        val cleaned = rawAddress.replace("+", " ").trim()
        val segments = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return emptyList()

        val candidates = mutableListOf<String>()
        candidates.add(cleaned)

        // Drop front segments progressively (shop name -> road name -> layout name)
        for (i in 1 until segments.size) {
            val sub = segments.drop(i).joinToString(", ")
            if (sub.isNotBlank() && !candidates.contains(sub)) {
                candidates.add(sub)
            }
        }

        // Also try first segment + last 2 segments (POI name + City, State)
        if (segments.size >= 3) {
            val poiCity = "${segments.first()}, ${segments[segments.size - 2]}"
            if (!candidates.contains(poiCity)) {
                candidates.add(poiCity)
            }
        }

        return candidates
    }

    private fun queryNominatim(query: String): Pair<Double, Double>? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1&countrycodes=in")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "WakeMe-App/2.0 (Android; Commute Alarm)")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)
                    if (isValidCoordinate(lat, lon)) {
                        return Pair(lat, lon)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryPhoton(query: String): Pair<Double, Double>? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://photon.komoot.io/api/?q=$encoded&limit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "WakeMe-App/2.0")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val features = obj.optJSONArray("features")
                if (features != null && features.length() > 0) {
                    val feature = features.getJSONObject(0)
                    val geom = feature.getJSONObject("geometry")
                    val coords = geom.getJSONArray("coordinates")
                    val lon = coords.getDouble(0)
                    val lat = coords.getDouble(1)
                    if (isValidCoordinate(lat, lon)) {
                        return Pair(lat, lon)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun extractPlaceNameFromUrl(url: String): String? {
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
        return lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)
    }
}
