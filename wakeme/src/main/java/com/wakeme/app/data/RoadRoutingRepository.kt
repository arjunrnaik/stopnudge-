package com.wakeme.app.data

import com.wakeme.app.data.local.transit.NammaMetroNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class NearestMetroRoadResult(
    val station: NammaMetroNetwork.ActiveStation,
    val roadDistanceMeters: Float,
    val durationSeconds: Float,
    val routePoints: List<GeoPoint> = emptyList(),
    val isRoadAccurate: Boolean = true
)

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
                            id = "fastest",
                            isFastest = true,
                            summary = "⚡ Fastest (${(sorted.first().durationSeconds / 60).toInt().coerceAtLeast(1)}m)"
                        )

                        val alternatives = sorted.drop(1).mapIndexed { idx, route ->
                            val deltaSec = route.durationSeconds - fastest.durationSeconds
                            val deltaMin = (deltaSec / 60).toInt()
                            val summaryStr = if (deltaMin > 0) "+$deltaMin min" else "Alt ${idx + 1}"
                            route.copy(id = "alt_$idx", summary = summaryStr)
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

    /**
     * Finds the nearest metro station by calculating real road travel distance
     * through the actual road network (one-to-many isochrone/matrix routing)
     * instead of relying on aerial straight-line radius.
     */
    suspend fun findNearestMetroStationByRoad(
        origin: GeoPoint,
        candidateStations: List<NammaMetroNetwork.ActiveStation> = NammaMetroNetwork.ACTIVE_STATIONS
    ): NearestMetroRoadResult = withContext(Dispatchers.IO) {
        if (candidateStations.isEmpty()) {
            val defaultStation = NammaMetroNetwork.ACTIVE_STATIONS[0]
            return@withContext NearestMetroRoadResult(defaultStation, 0f, 0f, emptyList(), false)
        }

        // 1. Pre-filter top 6 candidate stations by straight-line distance
        val sortedCandidates = candidateStations.map { station ->
            val straightMeters = haversineDistanceMeters(
                origin.latitude, origin.longitude,
                station.latitude, station.longitude
            )
            Pair(station, straightMeters)
        }.sortedBy { it.second }.take(6)

        try {
            // 2. Query OSRM Table API in driving mode with distance & duration annotations
            val coordsStr = buildString {
                append(String.format(Locale.US, "%.6f,%.6f", origin.longitude, origin.latitude))
                for ((station, _) in sortedCandidates) {
                    append(";")
                    append(String.format(Locale.US, "%.6f,%.6f", station.longitude, station.latitude))
                }
            }

            val tableUrlString = "https://router.project-osrm.org/table/v1/driving/$coordsStr?sources=0&annotations=distance,duration"
            val url = URL(tableUrlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WakeMe-App/2.0")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optString("code") == "Ok") {
                    val distancesArray = json.optJSONArray("distances")?.optJSONArray(0)
                    val durationsArray = json.optJSONArray("durations")?.optJSONArray(0)

                    if (distancesArray != null && durationsArray != null && distancesArray.length() > 1) {
                        var bestIndex = -1
                        var minRoadDistance = Float.MAX_VALUE
                        var bestDuration = 0f

                        // Index 0 is source to source (0 distance). Indices 1..N correspond to candidate stations
                        for (i in 0 until sortedCandidates.size) {
                            val arrayIndex = i + 1
                            if (!distancesArray.isNull(arrayIndex)) {
                                val dist = distancesArray.getDouble(arrayIndex).toFloat()
                                val dur = if (!durationsArray.isNull(arrayIndex)) durationsArray.getDouble(arrayIndex).toFloat() else 0f
                                if (dist < minRoadDistance) {
                                    minRoadDistance = dist
                                    bestDuration = dur
                                    bestIndex = i
                                }
                            }
                        }

                        if (bestIndex >= 0) {
                            val winningStation = sortedCandidates[bestIndex].first
                            // Fetch exact road polyline for the winning station
                            val route = getRoute(origin.latitude, origin.longitude, winningStation.latitude, winningStation.longitude)
                            return@withContext NearestMetroRoadResult(
                                station = winningStation,
                                roadDistanceMeters = if (route.isRoadRoute) route.distanceMeters else minRoadDistance,
                                durationSeconds = if (route.isRoadRoute) route.durationSeconds else bestDuration,
                                routePoints = route.points,
                                isRoadAccurate = true
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Network failure or timeout -> fallback to curvature estimation
        }

        // Fallback: Use top candidate with road curvature factor (1.28x)
        val (fallbackStation, straightMeters) = sortedCandidates.first()
        val estimatedRoadDistance = (straightMeters * 1.28).toFloat()
        val estimatedDuration = estimatedRoadDistance / (30f * 1000f / 3600f)
        val directRoute = listOf(origin, fallbackStation.geoPoint)

        NearestMetroRoadResult(
            station = fallbackStation,
            roadDistanceMeters = estimatedRoadDistance,
            durationSeconds = estimatedDuration,
            routePoints = directRoute,
            isRoadAccurate = false
        )
    }

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
