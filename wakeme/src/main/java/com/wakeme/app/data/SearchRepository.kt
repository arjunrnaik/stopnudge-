package com.wakeme.app.data

import android.content.Context
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.data.local.transit.TransitDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

enum class SearchResultType {
    METRO, BUS, BUS_ROUTE, ONLINE_PLACE, GOOGLE_MAPS_SEARCH
}

data class SearchResultItem(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val type: SearchResultType,
    val routeNo: String? = null
)

class SearchRepository(context: Context) {

    private val transitDao = TransitDatabase.getInstance(context).transitDao()

    suspend fun search(
        query: String,
        centerLat: Double = 12.9716,
        centerLon: Double = 77.5946
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<SearchResultItem>()

        // 1. 0ms In-Memory Search across Active Namma Metro stations
        val lowerQuery = trimmed.lowercase()
        NammaMetroNetwork.ACTIVE_STATIONS
            .filter { it.name.lowercase().contains(lowerQuery) }
            .take(6)
            .forEach { m ->
                results.add(
                    SearchResultItem(
                        title = "${m.name} Metro Station",
                        subtitle = "Namma Metro • ${m.line} Line",
                        latitude = m.latitude,
                        longitude = m.longitude,
                        type = SearchResultType.METRO
                    )
                )
            }

        // 1b. 0ms Offline BMTC Route Number Search (e.g. 500-D, 335-E, KIA-9, MF-1)
        try {
            val matchedRoutes = transitDao.searchBusRoutes(trimmed)
            val dedup = LinkedHashSet<String>()
            for (r in matchedRoutes) {
                val key = "${r.routeNo.uppercase()}_${r.destination.uppercase()}"
                if (!dedup.add(key)) continue
                val destStopId = r.stopIds.split(',').lastOrNull()?.trim()
                val destStop = if (!destStopId.isNullOrEmpty()) transitDao.getBusStopById(destStopId) else null
                if (destStop != null) {
                    val timingPart = when {
                        r.freqLabel.isNotBlank() && r.firstBus.isNotBlank() -> "${r.freqLabel} (${r.firstBus}–${r.lastBus})"
                        r.freqLabel.isNotBlank() -> r.freqLabel
                        r.dailyTrips > 0 -> "${r.dailyTrips} trips/day"
                        else -> "${r.stopCount} stops"
                    }
                    results.add(
                        SearchResultItem(
                            title = "Bus ${r.routeNo} ➔ ${r.destination}",
                            subtitle = "${r.origin} ➔ ${r.destination} • $timingPart",
                            latitude = destStop.latitude,
                            longitude = destStop.longitude,
                            type = SearchResultType.BUS_ROUTE,
                            routeNo = r.routeNo
                        )
                    )
                }
                if (dedup.size >= 4) break
            }
        } catch (_: Exception) {}

        // 2. 0ms Offline SQLite Search across 10,156 BMTC bus stops
        try {
            val buses = transitDao.searchBusStops(trimmed)
            buses.take(6).forEach { b ->
                results.add(
                    SearchResultItem(
                        title = b.name,
                        subtitle = b.directionHint?.takeIf { it.isNotBlank() } ?: "BMTC Bus Stop",
                        latitude = b.latitude,
                        longitude = b.longitude,
                        type = SearchResultType.BUS
                    )
                )
            }
        } catch (_: Exception) {}

        // 3. Online Geocoding via Nominatim (rich street, tech park, and landmark data in India)
        if (trimmed.length >= 3) {
            try {
                val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&addressdetails=1"
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                    setRequestProperty("User-Agent", "WakeMe-App/2.0 (Android; Commute Alarm)")
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val lat = obj.optDouble("lat", 0.0)
                        val lon = obj.optDouble("lon", 0.0)
                        val displayName = obj.optString("display_name", "")
                        val name = obj.optString("name", displayName.substringBefore(","))
                        val parts = displayName.split(",").map { it.trim() }
                        val subtitle = if (parts.size > 1) parts.drop(1).take(3).joinToString(", ") else "Landmark / Area"

                        if (lat != 0.0 && lon != 0.0) {
                            val isDup = results.any {
                                abs(it.latitude - lat) < 0.0008 && abs(it.longitude - lon) < 0.0008
                            }
                            if (!isDup) {
                                results.add(
                                    SearchResultItem(
                                        title = name.ifBlank { displayName.substringBefore(",") },
                                        subtitle = subtitle,
                                        latitude = lat,
                                        longitude = lon,
                                        type = SearchResultType.ONLINE_PLACE
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Online Geocoding via Photon (fuzzy search with dynamic GPS viewport bias)
        if (trimmed.length >= 3) {
            try {
                val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                val urlString = "https://photon.komoot.io/api/?q=$encodedQuery&lat=$centerLat&lon=$centerLon&limit=4"
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("User-Agent", "WakeMe-App/2.0")
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val features = json.optJSONArray("features")
                    if (features != null) {
                        for (i in 0 until features.length()) {
                            val feature = features.getJSONObject(i)
                            val props = feature.getJSONObject("properties")
                            val geom = feature.getJSONObject("geometry")
                            val coords = geom.getJSONArray("coordinates")
                            val lon = coords.getDouble(0)
                            val lat = coords.getDouble(1)

                            val name = props.optString("name", "Unknown Location")
                            val city = props.optString("city", props.optString("state", ""))
                            val street = props.optString("street", "")
                            val subtitle = listOf(street, city).filter { it.isNotEmpty() }.joinToString(", ")

                            val isDup = results.any {
                                abs(it.latitude - lat) < 0.0008 && abs(it.longitude - lon) < 0.0008
                            }
                            if (!isDup) {
                                results.add(
                                    SearchResultItem(
                                        title = name,
                                        subtitle = if (subtitle.isNotEmpty()) subtitle else "OpenStreetMap Result",
                                        latitude = lat,
                                        longitude = lon,
                                        type = SearchResultType.ONLINE_PLACE
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. 1-Tap Google Maps Direct Search Shortcut
        if (trimmed.length >= 2) {
            results.add(
                SearchResultItem(
                    title = "Search \"$trimmed\" on Google Maps",
                    subtitle = "Open in Google Maps to pick any shop, cafe, or pin ➔",
                    latitude = 0.0,
                    longitude = 0.0,
                    type = SearchResultType.GOOGLE_MAPS_SEARCH
                )
            )
        }

        results
    }
}
