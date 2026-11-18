package com.wakeme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class RouteCandidate(
    val id: String,
    val distanceMeters: Float,
    val durationSeconds: Float,
    val points: List<GeoPoint>,
    val isFastest: Boolean = false,
    val summary: String = ""
)

data class RouteResult(
    val fastestRoute: RouteCandidate,
    val alternativeRoutes: List<RouteCandidate> = emptyList(),
    val isRoadRoute: Boolean = true
) {
    // Backward compatibility accessors
    val distanceMeters: Float get() = fastestRoute.distanceMeters
    val durationSeconds: Float get() = fastestRoute.durationSeconds
    val points: List<GeoPoint> get() = fastestRoute.points
}

object RoadRoutingRepository {

    /**
     * Fetches real road driving distance and route polyline coordinates via OSRM.
     * Evaluates multiple candidate alternatives and selects the earliest arrival (fastest) route.
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
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&alternatives=true",
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
                        val candidates = mutableListOf<RouteCandidate>()

                        for (i in 0 until routes.length()) {
                            val routeObj = routes.getJSONObject(i)
                            val roadDistance = routeObj.getDouble("distance").toFloat()
                            val duration = routeObj.getDouble("duration").toFloat()

                            val geometry = routeObj.getJSONObject("geometry")
                            val coords = geometry.getJSONArray("coordinates")
                            val polylinePoints = mutableListOf<GeoPoint>()

                            for (j in 0 until coords.length()) {
                                val coordPair = coords.getJSONArray(j)
                                val lon = coordPair.getDouble(0)
                                val lat = coordPair.getDouble(1)
                                polylinePoints.add(GeoPoint(lat, lon))
                            }

                            candidates.add(
                                RouteCandidate(
                                    id = "route_$i",
                                    distanceMeters = roadDistance,
                                    durationSeconds = duration,
                                    points = polylinePoints
                                )
                            )
                        }

                        // Sort by duration ascending: earliest arrival time comes first!
                        val sorted = candidates.sortedBy { it.durationSeconds }
                        val fastest = sorted.first().copy(
                            isFastest = true,
                            summary = "⚡ Fastest (${(sorted.first().durationSeconds / 60).toInt().coerceAtLeast(1)}m)"
                        )

                        val alternatives = sorted.drop(1).map { route ->
                            val deltaSec = route.durationSeconds - fastest.durationSeconds
                            val deltaMin = (deltaSec / 60).toInt()
                            val summaryStr = if (deltaMin > 0) "+$deltaMin min" else "Alt"
                            route.copy(summary = summaryStr)
                        }

                        return@withContext RouteResult(
                            fastestRoute = fastest,
                            alternativeRoutes = alternatives,
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
        val fallbackRoute = RouteCandidate(
            id = "fallback",
            distanceMeters = estimatedRoadDistance,
            durationSeconds = estimatedDuration,
            points = directPoints,
            isFastest = true,
            summary = "Direct"
        )

        RouteResult(
            fastestRoute = fallbackRoute,
            alternativeRoutes = emptyList(),
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
