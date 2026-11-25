package com.wakeme.app.data

import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.model.VehicleMode
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

data class PolylineProgress(
    val remainingMeters: Float,
    val offRouteMeters: Double,
    val matchedSegmentIndex: Int
)

data class ArrivalTriggerDecision(
    val shouldTrigger: Boolean,
    val effectiveDistanceMeters: Float,
    val reason: String
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

    /**
     * Evaluates whether the arrival reminder should trigger based on the vehicle mode.
     * - Cab / Auto (road modes): Triggers strictly on remaining road distance, with a
     *   micro-arrival gate safety net (120m) so pins slightly off the street never miss.
     * - Metro / Bus: Triggers on station-centric radius geofence.
     */
    fun evaluateArrivalTrigger(
        vehicleMode: VehicleMode,
        targetThresholdMeters: Float,
        straightLineMeters: Float,
        roadDistanceMeters: Float?,
        hasActiveRoadRoute: Boolean,
        microArrivalGateMeters: Float = 120f
    ): ArrivalTriggerDecision {
        if (!vehicleMode.usesRoadDistanceTrigger) {
            val trigger = straightLineMeters <= targetThresholdMeters
            return ArrivalTriggerDecision(
                shouldTrigger = trigger,
                effectiveDistanceMeters = straightLineMeters,
                reason = if (trigger) "Entered ${targetThresholdMeters.toInt()}m station radius" else "Outside station radius"
            )
        }

        // Road-based trigger for Cab / Auto / Direct Road
        if (roadDistanceMeters != null && hasActiveRoadRoute) {
            val hitRoadThreshold = roadDistanceMeters <= targetThresholdMeters
            val hitMicroGate = straightLineMeters <= microArrivalGateMeters
            return ArrivalTriggerDecision(
                shouldTrigger = hitRoadThreshold || hitMicroGate,
                effectiveDistanceMeters = roadDistanceMeters,
                reason = when {
                    hitRoadThreshold -> "Road distance ${roadDistanceMeters.toInt()}m <= ${targetThresholdMeters.toInt()}m"
                    hitMicroGate -> "Arrived at destination gate (${straightLineMeters.toInt()}m straight-line)"
                    else -> "${roadDistanceMeters.toInt()}m remaining along road"
                }
            )
        }

        // Offline / fallback road curvature estimate (1.28x urban winding factor)
        val estimatedRoad = straightLineMeters * 1.28f
        val hitFallback = estimatedRoad <= targetThresholdMeters || straightLineMeters <= microArrivalGateMeters
        return ArrivalTriggerDecision(
            shouldTrigger = hitFallback,
            effectiveDistanceMeters = estimatedRoad,
            reason = if (hitFallback) "Estimated road distance ${estimatedRoad.toInt()}m <= ${targetThresholdMeters.toInt()}m" else "${estimatedRoad.toInt()}m estimated road remaining"
        )
    }

    /**
     * Computes remaining road distance along an active polyline from a GPS fix.
     * Prefers forward progression from [minSegmentIndex] within 45m cross-track error
     * to prevent snapping across concrete dividers onto opposite U-turn lanes.
     */
    fun computeRemainingRoadDistanceAlongPolyline(
        lat: Double,
        lon: Double,
        polyline: List<GeoPoint>,
        minSegmentIndex: Int = 0
    ): PolylineProgress? {
        if (polyline.size < 2) return null

        val safeStart = minSegmentIndex.coerceIn(0, polyline.size - 2)

        fun findBestInRange(startIdx: Int, endIdxInclusive: Int): Triple<Int, Double, Double> {
            var bestIdx = startIdx
            var bestDist = Double.MAX_VALUE
            var bestT = 0.0
            for (i in startIdx..endIdxInclusive) {
                val p1 = polyline[i]
                val p2 = polyline[i + 1]
                val (dist, t) = projectOntoSegment(lat, lon, p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = i
                    bestT = t
                }
            }
            return Triple(bestIdx, bestDist, bestT)
        }

        // 1. Search local forward window (~25 segments or until end) first to stay on current lane
        val localEnd = (safeStart + 25).coerceAtMost(polyline.size - 2)
        val localMatch = findBestInRange(safeStart, localEnd)

        val (matchedIdx, offRouteDist, matchedT) = if (localMatch.second <= 45.0) {
            localMatch
        } else {
            // Fall back to global search across entire polyline
            findBestInRange(0, polyline.size - 2)
        }

        val segStart = polyline[matchedIdx]
        val segEnd = polyline[matchedIdx + 1]
        val projLat = segStart.latitude + matchedT * (segEnd.latitude - segStart.latitude)
        val projLon = segStart.longitude + matchedT * (segEnd.longitude - segStart.longitude)

        var remaining = haversineDistanceMeters(projLat, projLon, segEnd.latitude, segEnd.longitude)
        for (k in (matchedIdx + 1) until (polyline.size - 1)) {
            val a = polyline[k]
            val b = polyline[k + 1]
            remaining += haversineDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }

        return PolylineProgress(
            remainingMeters = remaining.toFloat(),
            offRouteMeters = offRouteDist,
            matchedSegmentIndex = matchedIdx
        )
    }

    /**
     * Extracts the final [alertMeters] stretch of a road polyline leading up to the destination,
     * so maps can visually highlight where along the road the alarm will trigger.
     */
    fun extractFinalAlertSegment(points: List<GeoPoint>, alertMeters: Float): List<GeoPoint> {
        if (points.size < 2 || alertMeters <= 0f) return emptyList()

        val reversedResult = mutableListOf(points.last())
        var accumulated = 0.0

        for (i in points.size - 1 downTo 1) {
            val endPt = points[i]
            val startPt = points[i - 1]
            val segLen = haversineDistanceMeters(startPt.latitude, startPt.longitude, endPt.latitude, endPt.longitude)

            if (accumulated + segLen >= alertMeters) {
                val needed = alertMeters - accumulated
                val fracFromEnd = if (segLen > 1e-6) (needed / segLen).coerceIn(0.0, 1.0) else 0.0
                val cutLat = endPt.latitude + fracFromEnd * (startPt.latitude - endPt.latitude)
                val cutLon = endPt.longitude + fracFromEnd * (startPt.longitude - endPt.longitude)
                reversedResult.add(GeoPoint(cutLat, cutLon))
                break
            } else {
                reversedResult.add(startPt)
                accumulated += segLen
            }
        }

        return reversedResult.reversed()
    }

    private fun projectOntoSegment(
        lat: Double, lon: Double,
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Pair<Double, Double> {
        val cosLat = Math.cos(Math.toRadians(lat1))
        val mPerDegLat = 111320.0
        val mPerDegLon = 111320.0 * cosLat

        val x = (lon - lon1) * mPerDegLon
        val y = (lat - lat1) * mPerDegLat
        val dx = (lon2 - lon1) * mPerDegLon
        val dy = (lat2 - lat1) * mPerDegLat

        val segLenSq = dx * dx + dy * dy
        if (segLenSq < 1e-6) {
            return Pair(Math.hypot(x, y), 0.0)
        }

        val t = ((x * dx + y * dy) / segLenSq).coerceIn(0.0, 1.0)
        val projX = t * dx
        val projY = t * dy
        return Pair(Math.hypot(x - projX, y - projY), t)
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
