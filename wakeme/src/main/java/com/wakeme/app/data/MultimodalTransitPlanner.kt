package com.wakeme.app.data

import com.wakeme.app.data.local.transit.NammaMetroNetwork
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class MultimodalLegType(val iconEmoji: String, val label: String) {
    WALK("🚶", "Walk"),
    METRO("🚇", "Namma Metro"),
    CAB("🚕", "Cab / Auto"),
    BUS("🚌", "City Bus")
}

data class MultimodalLeg(
    val type: MultimodalLegType,
    val title: String,
    val subtitle: String,
    val distanceMeters: Double,
    val durationMinutes: Int,
    val startLocation: GeoPoint,
    val endLocation: GeoPoint,
    val lineName: String? = null,
    val stopsCount: Int = 0
)

data class MultimodalTripPlan(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val destinationName: String,
    val entryStation: NammaMetroNetwork.ActiveStation,
    val exitStation: NammaMetroNetwork.ActiveStation,
    val transferStation: NammaMetroNetwork.ActiveStation? = null,
    val legs: List<MultimodalLeg>,
    val totalDurationMinutes: Int,
    val totalDistanceMeters: Double,
    val summary: String,
    val routePolyline: List<GeoPoint>,
    val isSameStationCatchment: Boolean = false
)

object MultimodalTransitPlanner {

    /**
     * Plans a Google Maps-style multimodal transit journey combining:
     * - First Mile: Walk or Cab / Auto to nearest entry Metro station
     * - Rapid Transit Leg: Namma Metro (Purple, Green, Yellow, Pink lines with transfer if needed)
     * - Last Mile: Walk or Cab / Auto from exit Metro station to final destination
     */
    fun planJourney(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String = "Destination"
    ): MultimodalTripPlan {
        val (entryStation, entryDist) = NammaMetroNetwork.findNearestActiveStation(origin)
        val (exitStation, exitDist) = NammaMetroNetwork.findNearestActiveStation(destination)

        val directDist = haversineDistanceMeters(origin, destination)

        // Case 1: Same station catchment area (Origin and Destination are both close to the same Metro station)
        if (entryStation.id == exitStation.id) {
            val isWalk = directDist <= 1200.0
            val durationMin = if (isWalk) {
                (directDist / 75.0).toInt().coerceAtLeast(1)
            } else {
                (directDist / 400.0).toInt().coerceAtLeast(3)
            }
            val legType = if (isWalk) MultimodalLegType.WALK else MultimodalLegType.CAB
            val leg = MultimodalLeg(
                type = legType,
                title = if (isWalk) "Direct Walk to $destinationName" else "Direct Cab / Auto to $destinationName",
                subtitle = formatDistanceDuration(directDist, durationMin),
                distanceMeters = directDist,
                durationMinutes = durationMin,
                startLocation = origin,
                endLocation = destination
            )
            return MultimodalTripPlan(
                origin = origin,
                destination = destination,
                destinationName = destinationName,
                entryStation = entryStation,
                exitStation = exitStation,
                transferStation = null,
                legs = listOf(leg),
                totalDurationMinutes = durationMin,
                totalDistanceMeters = directDist,
                summary = "${legType.iconEmoji} ${durationMin}m direct",
                routePolyline = listOf(origin, destination),
                isSameStationCatchment = true
            )
        }

        // Case 2: Multi-leg Journey (Origin -> Entry Station -> Metro -> Exit Station -> Destination)
        val legs = mutableListOf<MultimodalLeg>()

        // --- Leg 1: First Mile to Entry Metro Station ---
        val leg1Walk = entryDist <= 1200.0
        val leg1Duration = if (leg1Walk) {
            (entryDist / 75.0).toInt().coerceAtLeast(1)
        } else {
            (entryDist / 400.0).toInt().coerceAtLeast(3)
        }
        val leg1Type = if (leg1Walk) MultimodalLegType.WALK else MultimodalLegType.CAB
        val leg1Title = if (leg1Walk) "Walk to ${entryStation.name} Metro" else "Cab / Auto to ${entryStation.name} Metro"
        legs.add(
            MultimodalLeg(
                type = leg1Type,
                title = leg1Title,
                subtitle = formatDistanceDuration(entryDist, leg1Duration),
                distanceMeters = entryDist,
                durationMinutes = leg1Duration,
                startLocation = origin,
                endLocation = entryStation.geoPoint
            )
        )

        // --- Leg 2: Metro Transit Ride ---
        val entryLine = primaryLineName(entryStation.line)
        val exitLine = primaryLineName(exitStation.line)
        val isSameLine = entryLine == exitLine || entryStation.line.contains(exitLine) || exitStation.line.contains(entryLine)

        var transferStation: NammaMetroNetwork.ActiveStation? = null
        val metroLegs = mutableListOf<MultimodalLeg>()
        var metroTotalDuration = 0
        var metroTotalDist = 0.0

        if (isSameLine) {
            // Direct ride on same line
            val lineStations = getStationsForLine(entryLine)
            val idx1 = lineStations.indexOfFirst { it.id == entryStation.id }.coerceAtLeast(0)
            val idx2 = lineStations.indexOfFirst { it.id == exitStation.id }.coerceAtLeast(0)
            val stopsCount = abs(idx1 - idx2).coerceAtLeast(1)
            val rideDist = haversineDistanceMeters(entryStation.geoPoint, exitStation.geoPoint)
            val rideDuration = (stopsCount * 2 + 1).coerceAtLeast(3) // ~2 mins per station + 1 min dwell

            metroTotalDuration += rideDuration
            metroTotalDist += rideDist

            metroLegs.add(
                MultimodalLeg(
                    type = MultimodalLegType.METRO,
                    title = "Namma Metro $entryLine Line ($stopsCount stops)",
                    subtitle = "Board at ${entryStation.name} ➔ Deboard at ${exitStation.name}",
                    distanceMeters = rideDist,
                    durationMinutes = rideDuration,
                    startLocation = entryStation.geoPoint,
                    endLocation = exitStation.geoPoint,
                    lineName = entryLine,
                    stopsCount = stopsCount
                )
            )
        } else {
            // Transfer required between different Metro lines
            transferStation = findInterchangeStation(entryLine, exitLine)
            val transfer = transferStation ?: NammaMetroNetwork.ACTIVE_STATIONS.first { it.id == "metro_p_15" } // fallback to Majestic

            // Leg 2a: Line 1 to Transfer Station
            val line1Stations = getStationsForLine(entryLine)
            val idx1 = line1Stations.indexOfFirst { it.id == entryStation.id }.coerceAtLeast(0)
            val idxT1 = line1Stations.indexOfFirst { it.name.contains(transfer.name) || it.id == transfer.id }.coerceAtLeast(0)
            val stops1 = abs(idx1 - idxT1).coerceAtLeast(1)
            val dist1 = haversineDistanceMeters(entryStation.geoPoint, transfer.geoPoint)
            val dur1 = (stops1 * 2 + 1).coerceAtLeast(3)

            metroLegs.add(
                MultimodalLeg(
                    type = MultimodalLegType.METRO,
                    title = "$entryLine Line to ${transfer.name} ($stops1 stops)",
                    subtitle = "Board at ${entryStation.name} ➔ Transfer at ${transfer.name}",
                    distanceMeters = dist1,
                    durationMinutes = dur1,
                    startLocation = entryStation.geoPoint,
                    endLocation = transfer.geoPoint,
                    lineName = entryLine,
                    stopsCount = stops1
                )
            )

            // Leg 2b: Line 2 from Transfer Station to Exit Station
            val line2Stations = getStationsForLine(exitLine)
            val idxT2 = line2Stations.indexOfFirst { it.name.contains(transfer.name) || it.id == transfer.id }.coerceAtLeast(0)
            val idx2 = line2Stations.indexOfFirst { it.id == exitStation.id }.coerceAtLeast(0)
            val stops2 = abs(idxT2 - idx2).coerceAtLeast(1)
            val dist2 = haversineDistanceMeters(transfer.geoPoint, exitStation.geoPoint)
            val dur2 = (stops2 * 2 + 1).coerceAtLeast(3)

            metroLegs.add(
                MultimodalLeg(
                    type = MultimodalLegType.METRO,
                    title = "$exitLine Line to ${exitStation.name} ($stops2 stops)",
                    subtitle = "Transfer at ${transfer.name} ➔ Deboard at ${exitStation.name}",
                    distanceMeters = dist2,
                    durationMinutes = dur2 + 3, // +3 mins interchange platform walk
                    startLocation = transfer.geoPoint,
                    endLocation = exitStation.geoPoint,
                    lineName = exitLine,
                    stopsCount = stops2
                )
            )

            metroTotalDuration += (dur1 + dur2 + 3)
            metroTotalDist += (dist1 + dist2)
        }

        legs.addAll(metroLegs)

        // --- Leg 3: Last Mile from Exit Metro Station to Destination ---
        val leg3Walk = exitDist <= 1200.0
        val leg3Duration = if (leg3Walk) {
            (exitDist / 75.0).toInt().coerceAtLeast(1)
        } else {
            (exitDist / 400.0).toInt().coerceAtLeast(3)
        }
        val leg3Type = if (leg3Walk) MultimodalLegType.WALK else MultimodalLegType.CAB
        val leg3Title = if (leg3Walk) "Walk to $destinationName" else "Cab / Auto to $destinationName"
        legs.add(
            MultimodalLeg(
                type = leg3Type,
                title = leg3Title,
                subtitle = formatDistanceDuration(exitDist, leg3Duration),
                distanceMeters = exitDist,
                durationMinutes = leg3Duration,
                startLocation = exitStation.geoPoint,
                endLocation = destination
            )
        )

        val totalDuration = leg1Duration + metroTotalDuration + leg3Duration
        val totalDist = entryDist + metroTotalDist + exitDist
        val summaryChip = "${leg1Type.iconEmoji} ${leg1Duration}m ➔ 🚇 ${metroTotalDuration}m ➔ ${leg3Type.iconEmoji} ${leg3Duration}m"

        // Polyline connecting origin -> entry station -> exit station -> destination
        val polyline = mutableListOf<GeoPoint>()
        polyline.add(origin)
        polyline.add(entryStation.geoPoint)
        if (transferStation != null) {
            polyline.add(transferStation.geoPoint)
        }
        polyline.add(exitStation.geoPoint)
        polyline.add(destination)

        return MultimodalTripPlan(
            origin = origin,
            destination = destination,
            destinationName = destinationName,
            entryStation = entryStation,
            exitStation = exitStation,
            transferStation = transferStation,
            legs = legs,
            totalDurationMinutes = totalDuration,
            totalDistanceMeters = totalDist,
            summary = summaryChip,
            routePolyline = polyline,
            isSameStationCatchment = false
        )
    }

    private fun findInterchangeStation(lineA: String, lineB: String): NammaMetroNetwork.ActiveStation {
        val pair = setOf(lineA.lowercase(), lineB.lowercase())
        return when {
            pair.contains("purple") && pair.contains("green") -> {
                NammaMetroNetwork.ACTIVE_STATIONS.firstOrNull { it.id == "metro_p_15" }
                    ?: NammaMetroNetwork.ACTIVE_STATIONS[14] // Majestic
            }
            pair.contains("green") && pair.contains("yellow") -> {
                NammaMetroNetwork.ACTIVE_STATIONS.firstOrNull { it.id == "metro_g_23" }
                    ?: NammaMetroNetwork.ACTIVE_STATIONS[14] // RV Road
            }
            pair.contains("yellow") && pair.contains("pink") -> {
                NammaMetroNetwork.ACTIVE_STATIONS.firstOrNull { it.id == "metro_y_02" }
                    ?: NammaMetroNetwork.ACTIVE_STATIONS[14] // Jayadeva Hospital
            }
            else -> {
                // Default interchange hub is Majestic
                NammaMetroNetwork.ACTIVE_STATIONS.firstOrNull { it.id == "metro_p_15" }
                    ?: NammaMetroNetwork.ACTIVE_STATIONS[14]
            }
        }
    }

    private fun getStationsForLine(line: String): List<NammaMetroNetwork.ActiveStation> {
        val lower = line.lowercase()
        return NammaMetroNetwork.ACTIVE_STATIONS.filter { it.line.lowercase().contains(lower) }
    }

    private fun primaryLineName(lineStr: String): String {
        val lower = lineStr.lowercase()
        return when {
            lower.contains("purple") -> "Purple"
            lower.contains("green") -> "Green"
            lower.contains("yellow") -> "Yellow"
            lower.contains("pink") -> "Pink"
            else -> "Purple"
        }
    }

    private fun formatDistanceDuration(distMeters: Double, durationMin: Int): String {
        val distStr = if (distMeters >= 1000) {
            String.format("%.1f km", distMeters / 1000.0)
        } else {
            "${distMeters.toInt()} m"
        }
        return "$distStr (~$durationMin mins)"
    }

    private fun haversineDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
