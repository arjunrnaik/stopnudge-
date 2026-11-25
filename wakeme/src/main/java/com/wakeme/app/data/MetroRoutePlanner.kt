package com.wakeme.app.data

import com.wakeme.app.data.local.transit.NammaMetroNetwork
import org.osmdroid.util.GeoPoint
import java.util.PriorityQueue

data class MetroLeg(
    val lineName: String,
    val lineColorInt: Int,
    val stations: List<NammaMetroNetwork.ActiveStation>,
    val directionLabel: String,
    val isBusLeg: Boolean = false
) {
    val stopsCount: Int get() = (stations.size - 1).coerceAtLeast(0)
    val boardingStation: NammaMetroNetwork.ActiveStation get() = stations.first()
    val alightingStation: NammaMetroNetwork.ActiveStation get() = stations.last()
    val trackPoints: List<GeoPoint> get() = stations.map { it.geoPoint }
}

data class MetroJourneyPlan(
    val boardingStation: NammaMetroNetwork.ActiveStation,
    val exitStation: NammaMetroNetwork.ActiveStation,
    val legs: List<MetroLeg>,
    val totalStops: Int,
    val requiresLineChange: Boolean,
    val interchangeStations: List<NammaMetroNetwork.ActiveStation>,
    val destinationPinName: String,
    val destinationPinPoint: GeoPoint,
    val exitWalkDistanceMeters: Float,
    val isBusJourney: Boolean = false
) {
    fun getAlertStopForLeg(legIndex: Int, stopsBeforeAlert: Int): NammaMetroNetwork.ActiveStation? {
        val leg = legs.getOrNull(legIndex) ?: return null
        val isInterchangeLeg = legIndex < legs.size - 1
        // For intermediate interchanges, alert at 1 stop before (or min available stops on short leg)
        val effectiveStopsBefore = if (isInterchangeLeg) {
            stopsBeforeAlert.coerceIn(1, leg.stopsCount.coerceAtLeast(1))
        } else {
            stopsBeforeAlert.coerceIn(0, leg.stopsCount)
        }
        val idx = (leg.stations.size - 1 - effectiveStopsBefore).coerceIn(0, leg.stations.size - 1)
        return leg.stations[idx]
    }
}

data class MetroLegProgress(
    val currentStationIndex: Int,
    val currentStation: NammaMetroNetwork.ActiveStation,
    val nextStation: NammaMetroNetwork.ActiveStation?,
    val stopsRemaining: Int,
    val distanceToAlertStopMeters: Float,
    val distanceToEndMeters: Float,
    val alertStopStation: NammaMetroNetwork.ActiveStation,
    val shouldTriggerStopAlert: Boolean
)

/**
 * Offline Namma Metro Graph & Stop-to-Stop Journey Planner.
 * Supports single-line trips and automatic 1- or 2-transfer multi-line journeys
 * across Purple, Green, Yellow, and Pink lines (Majestic, RV Road, Jayadeva Hospital).
 */
object MetroRoutePlanner {

    const val BOARDING_PROXIMITY_METERS = 50f
    const val STOP_APPROACH_TRIGGER_METERS = 250f
    const val AT_STATION_TRIGGER_METERS = 50f

    val lineStations: Map<String, List<NammaMetroNetwork.ActiveStation>> by lazy {
        mapOf(
            "Purple" to mapTrackToStations(NammaMetroNetwork.TRACK_PURPLE),
            "Green" to mapTrackToStations(NammaMetroNetwork.TRACK_GREEN),
            "Yellow" to mapTrackToStations(NammaMetroNetwork.TRACK_YELLOW),
            "Pink" to mapTrackToStations(NammaMetroNetwork.TRACK_PINK)
        )
    }

    private fun mapTrackToStations(track: List<GeoPoint>): List<NammaMetroNetwork.ActiveStation> {
        return track.map { pt ->
            NammaMetroNetwork.ACTIVE_STATIONS.minBy { st ->
                RoadRoutingRepository.haversineDistanceMeters(pt.latitude, pt.longitude, st.latitude, st.longitude)
            }
        }
    }

    /**
     * Plans a stop-to-stop Metro journey between [boardingStation] and [exitStation],
     * using Dijkstra over (stationId, lineName) states to minimize stops and line transfers.
     */
    fun planJourney(
        boardingStation: NammaMetroNetwork.ActiveStation,
        exitStation: NammaMetroNetwork.ActiveStation,
        destinationPinName: String = "${exitStation.name} Metro Station",
        destinationPinPoint: GeoPoint = exitStation.geoPoint
    ): MetroJourneyPlan {
        val exitWalkDist = RoadRoutingRepository.haversineDistanceMeters(
            exitStation.latitude, exitStation.longitude,
            destinationPinPoint.latitude, destinationPinPoint.longitude
        ).toFloat()

        if (boardingStation.id == exitStation.id) {
            val line = primaryLineName(boardingStation)
            val leg = MetroLeg(
                lineName = line,
                lineColorInt = NammaMetroNetwork.getLineColor(line),
                stations = listOf(boardingStation),
                directionLabel = "At ${boardingStation.name}"
            )
            return MetroJourneyPlan(
                boardingStation = boardingStation,
                exitStation = exitStation,
                legs = listOf(leg),
                totalStops = 0,
                requiresLineChange = false,
                interchangeStations = emptyList(),
                destinationPinName = destinationPinName,
                destinationPinPoint = destinationPinPoint,
                exitWalkDistanceMeters = exitWalkDist
            )
        }

        // Build adjacency across lines
        data class Node(val stationId: String, val lineName: String)
        data class State(val node: Node, val cost: Int)

        val dist = mutableMapOf<Node, Int>()
        val prev = mutableMapOf<Node, Node>()
        val pq = PriorityQueue<State>(compareBy { it.cost })

        // Seed all lines that contain boardingStation
        val startNodes = mutableListOf<Node>()
        for ((line, stations) in lineStations) {
            if (stations.any { it.id == boardingStation.id }) {
                val n = Node(boardingStation.id, line)
                dist[n] = 0
                pq.add(State(n, 0))
                startNodes.add(n)
            }
        }

        var goalNode: Node? = null
        while (pq.isNotEmpty()) {
            val cur = pq.poll()!!
            if (cur.cost > (dist[cur.node] ?: Int.MAX_VALUE)) continue

            if (cur.node.stationId == exitStation.id) {
                goalNode = cur.node
                break
            }

            val stationsOnLine = lineStations[cur.node.lineName].orEmpty()
            val idx = stationsOnLine.indexOfFirst { it.id == cur.node.stationId }
            if (idx >= 0) {
                // Neighbors along the same line (+1 cost per stop)
                val neighbors = listOfNotNull(
                    stationsOnLine.getOrNull(idx - 1),
                    stationsOnLine.getOrNull(idx + 1)
                )
                for (nb in neighbors) {
                    val nextNode = Node(nb.id, cur.node.lineName)
                    val newCost = cur.cost + 1
                    if (newCost < (dist[nextNode] ?: Int.MAX_VALUE)) {
                        dist[nextNode] = newCost
                        prev[nextNode] = cur.node
                        pq.add(State(nextNode, newCost))
                    }
                }
            }

            // Transfers at the current station to any other line (+4 penalty to avoid unnecessary transfers)
            for ((otherLine, otherStations) in lineStations) {
                if (otherLine != cur.node.lineName && otherStations.any { it.id == cur.node.stationId }) {
                    val transferNode = Node(cur.node.stationId, otherLine)
                    val newCost = cur.cost + 4
                    if (newCost < (dist[transferNode] ?: Int.MAX_VALUE)) {
                        dist[transferNode] = newCost
                        prev[transferNode] = cur.node
                        pq.add(State(transferNode, newCost))
                    }
                }
            }
        }

        // Reconstruct path into legs
        val nodePath = mutableListOf<Node>()
        var cursor = goalNode
        while (cursor != null) {
            nodePath.add(cursor)
            cursor = prev[cursor]
        }
        nodePath.reverse()

        val stationById = NammaMetroNetwork.ACTIVE_STATIONS.associateBy { it.id }
        val legs = mutableListOf<MetroLeg>()
        val interchanges = mutableListOf<NammaMetroNetwork.ActiveStation>()

        if (nodePath.isNotEmpty()) {
            var currentLine = nodePath.first().lineName
            var currentLegStations = mutableListOf<NammaMetroNetwork.ActiveStation>()

            for (node in nodePath) {
                val st = stationById[node.stationId] ?: continue
                if (node.lineName != currentLine) {
                    // Finish previous leg at current interchange station
                    if (currentLegStations.isNotEmpty()) {
                        legs.add(buildLeg(currentLine, currentLegStations))
                        interchanges.add(currentLegStations.last())
                    }
                    currentLine = node.lineName
                    currentLegStations = mutableListOf(st)
                } else {
                    if (currentLegStations.lastOrNull()?.id != st.id) {
                        currentLegStations.add(st)
                    }
                }
            }
            if (currentLegStations.isNotEmpty()) {
                legs.add(buildLeg(currentLine, currentLegStations))
            }
        }

        val totalStops = legs.sumOf { it.stopsCount }
        return MetroJourneyPlan(
            boardingStation = boardingStation,
            exitStation = exitStation,
            legs = legs,
            totalStops = totalStops,
            requiresLineChange = legs.size > 1,
            interchangeStations = interchanges,
            destinationPinName = destinationPinName,
            destinationPinPoint = destinationPinPoint,
            exitWalkDistanceMeters = exitWalkDist
        )
    }

    private fun buildLeg(lineName: String, stations: List<NammaMetroNetwork.ActiveStation>): MetroLeg {
        val fullLine = lineStations[lineName].orEmpty()
        val directionLabel = if (stations.size >= 2 && fullLine.size >= 2) {
            val firstIdx = fullLine.indexOfFirst { it.id == stations.first().id }
            val lastIdx = fullLine.indexOfFirst { it.id == stations.last().id }
            if (lastIdx > firstIdx) {
                "Towards ${fullLine.last().name}"
            } else {
                "Towards ${fullLine.first().name}"
            }
        } else {
            "$lineName Line"
        }

        return MetroLeg(
            lineName = lineName,
            lineColorInt = NammaMetroNetwork.getLineColor(lineName),
            stations = stations.toList(),
            directionLabel = directionLabel
        )
    }

    private fun primaryLineName(station: NammaMetroNetwork.ActiveStation): String {
        val lower = station.line.lowercase()
        return when {
            lower.contains("purple") -> "Purple"
            lower.contains("green") -> "Green"
            lower.contains("yellow") -> "Yellow"
            lower.contains("pink") -> "Pink"
            else -> "Purple"
        }
    }

    /**
     * Computes live stop-by-stop progress along [leg] from a GPS fix and checks whether the
     * commuter has reached the requested [stopsBeforeAlert] stop (or 50m for 0 stops before).
     */
    fun evaluateMetroLegProgress(
        lat: Double,
        lon: Double,
        leg: MetroLeg,
        stopsBeforeAlert: Int,
        minStationIndex: Int = 0
    ): MetroLegProgress {
        val stations = leg.stations
        val lastIdx = (stations.size - 1).coerceAtLeast(0)
        val safeMin = minStationIndex.coerceIn(0, lastIdx)

        // Find closest station from safeMin onward
        var bestIdx = safeMin
        var bestDist = Double.MAX_VALUE
        for (i in safeMin until stations.size) {
            val st = stations[i]
            val d = RoadRoutingRepository.haversineDistanceMeters(lat, lon, st.latitude, st.longitude)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }

        // Only advance station index monotonically when reasonably close to a station or along track
        val currentIdx = bestIdx.coerceAtLeast(safeMin)
        val currentStation = stations[currentIdx]
        val nextStation = stations.getOrNull(currentIdx + 1)
        val stopsRemaining = (lastIdx - currentIdx).coerceAtLeast(0)

        val clampedStopsBefore = stopsBeforeAlert.coerceIn(0, lastIdx)
        val alertIdx = (lastIdx - clampedStopsBefore).coerceIn(0, lastIdx)
        val alertStop = stations[alertIdx]

        val distToAlert = RoadRoutingRepository.haversineDistanceMeters(
            lat, lon, alertStop.latitude, alertStop.longitude
        ).toFloat()
        val endStation = stations[lastIdx]
        val distToEnd = RoadRoutingRepository.haversineDistanceMeters(
            lat, lon, endStation.latitude, endStation.longitude
        ).toFloat()

        val shouldTrigger = when {
            clampedStopsBefore == 0 -> {
                // Strict 50m arrival trigger at the target stop
                distToEnd <= AT_STATION_TRIGGER_METERS
            }
            alertIdx == 0 -> {
                // Boarding directly at the alert stop itself (e.g. 1-stop trip): wait until approaching exit station
                distToEnd <= STOP_APPROACH_TRIGGER_METERS
            }
            else -> {
                // Trigger as soon as approaching within 250m of the N-stops-before station, OR already past it
                distToAlert <= STOP_APPROACH_TRIGGER_METERS || currentIdx >= alertIdx
            }
        }

        return MetroLegProgress(
            currentStationIndex = currentIdx,
            currentStation = currentStation,
            nextStation = nextStation,
            stopsRemaining = stopsRemaining,
            distanceToAlertStopMeters = distToAlert,
            distanceToEndMeters = distToEnd,
            alertStopStation = alertStop,
            shouldTriggerStopAlert = shouldTrigger
        )
    }
}
