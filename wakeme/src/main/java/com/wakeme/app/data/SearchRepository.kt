package com.wakeme.app.data

import android.content.Context
import com.wakeme.app.data.local.transit.TransitDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SearchResultType {
    METRO, BUS, ONLINE_PLACE
}

data class SearchResultItem(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val type: SearchResultType
)

class SearchRepository(context: Context) {

    private val transitDao = TransitDatabase.getInstance(context).transitDao()

    suspend fun search(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<SearchResultItem>()

        // 1. 0ms Offline SQLite Search across 83 Metro stations
        try {
            val metros = transitDao.searchMetroStations(trimmed)
            metros.take(6).forEach { m ->
                results.add(
                    SearchResultItem(
                        title = m.name,
                        subtitle = "Metro • ${m.metroLines}",
                        latitude = m.latitude,
                        longitude = m.longitude,
                        type = SearchResultType.METRO
                    )
                )
            }
        } catch (_: Exception) {}

        // 2. 0ms Offline SQLite Search across 5,062 BMTC bus stops
        try {
            val buses = transitDao.searchBusStops(trimmed)
            buses.take(8).forEach { b ->
                results.add(
                    SearchResultItem(
                        title = b.name,
                        subtitle = "BMTC Bus Stop",
                        latitude = b.latitude,
                        longitude = b.longitude,
                        type = SearchResultType.BUS
                    )
                )
            }
        } catch (_: Exception) {}

        // 3. Online Geocoding via Photon (OSM-based, free, zero keys) if query is longer than 2 chars
        if (trimmed.length >= 3) {
            try {
                val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                // Center biased around Bengaluru (12.9716, 77.5946)
                val urlString = "https://photon.komoot.io/api/?q=$encodedQuery&lat=12.9716&lon=77.5946&limit=5"
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
            } catch (_: Exception) {
                // Graceful fallback to offline results when offline
            }
        }

        results
    }
}
