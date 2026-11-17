package com.wakeme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class RouteResult(
    val distanceMeters: Float,
    val durationSeconds: Float,
    val points: List<GeoPoint>,
    val isRoadRoute: Boolean
)

object RoadRoutingRepository {

    /**
     * Fetches real road driving distance and route polyline coordinates via OSRM.
     * If offline or request fails, seamlessly falls back to road-curvature estimation.
     */
    suspend fun getRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double
    ): RouteResult = withContext(Dispatchers.IO) {
        val straightLineMeters = haversineDistanceMeters(startLat, startLon, destLat, destLon).toFloat()

        try {
            val urlString = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                startLon, startLat, destLon, destLat
            )
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WakeMe-App/2.0")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optString("code") == "Ok") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val firstRoute = routes.getJSONObject(0)
                        val roadDistance = firstRoute.getDouble("distance").toFloat()
                        val duration = firstRoute.getDouble("duration").toFloat()

                        val geometry = firstRoute.getJSONObject("geometry")
                        val coords = geometry.getJSONArray("coordinates")
                        val polylinePoints = mutableListOf<GeoPoint>()

                        for (i in 0 until coords.length()) {
                            val coordPair = coords.getJSONArray(i)
                            val lon = coordPair.getDouble(0)
                            val lat = coordPair.getDouble(1)
                            polylinePoints.add(GeoPoint(lat, lon))
                        }

                        return@withContext RouteResult(
                            distanceMeters = roadDistance,
                            durationSeconds = duration,
                            points = polylinePoints,
                            isRoadRoute = true
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Network failure or offline
        }

        // Fallback: Road winding curvature index (1.28x straight line for Indian urban roads)
        val estimatedRoadDistance = straightLineMeters * 1.28f
        val estimatedDuration = estimatedRoadDistance / (30f * 1000f / 3600f) // avg 30 km/h
        val directPoints = listOf(
            GeoPoint(startLat, startLon),
            GeoPoint(destLat, destLon)
        )

        RouteResult(
            distanceMeters = estimatedRoadDistance,
            durationSeconds = estimatedDuration,
            points = directPoints,
            isRoadRoute = false
        )
    }

    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
