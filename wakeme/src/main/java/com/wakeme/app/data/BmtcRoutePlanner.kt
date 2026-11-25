package com.wakeme.app.data

import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.BusRouteEntity
import com.wakeme.app.data.local.transit.BusStopEntity
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import org.osmdroid.util.GeoPoint
import kotlin.math.abs

data class ParsedBusRoute(
    val id: String,
    val routeNo: String,
    val origin: String,
    val destination: String,
    val stopIds: List<String>,
    val stopIndexById: Map<String, Int>,
    val category: BmtcBusCategory
)

data class BmtcRouteOption(
    val id: String,
    val routeTitle: String,
    val subtitle: String,
    val category: BmtcBusCategory,
    val plan: MetroJourneyPlan,
    val isTransferRoute: Boolean,
    val scoreMeters: Float
)

data class IndexedBmtcData(
    val stopById: Map<String, BusStopEntity>,
    val allStops: List<BusStopEntity>,
    val routes: List<ParsedBusRoute>,
    val stopToRouteIndices: Map<String, IntArray>
) {
    val isEmpty: Boolean get() = allStops.isEmpty() || routes.isEmpty()

    companion object {
        val EMPTY = IndexedBmtcData(emptyMap(), emptyList(), emptyList(), emptyMap())
    }
}

/**
 * Offline BMTC City Bus Graph & Stop-to-Stop Journey Planner.
 * Uses ordered route directions and opposite-side bus poles (~10,156 poles, ~10,096 route directions)
 * to recommend matching BMTC buses (direct & 1-transfer hubs) with 3-stage navigation plans.
 */
object BmtcRoutePlanner {

    private const val CANDIDATE_RADIUS_METERS = 1500.0
    private const val FALLBACK_RADIUS_METERS = 5000.0
    private const val TRANSFER_PENALTY_METERS = 900f
    private const val STOP_WEIGHT_METERS = 140f

    fun buildIndex(
        stops: List<BusStopEntity>,
        rawRoutes: List<BusRouteEntity>
    ): IndexedBmtcData {
        if (stops.isEmpty() || rawRoutes.isEmpty()) return IndexedBmtcData.EMPTY

        val stopById = HashMap<String, BusStopEntity>(stops.size * 2)
        for (stop in stops) {
            stopById[stop.id] = stop
        }

        val parsedRoutes = ArrayList<ParsedBusRoute>(rawRoutes.size)
        val inverted = HashMap<String, MutableList<Int>>(stops.size)

        for (raw in rawRoutes) {
            val tokens = raw.stopIds.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() && stopById.containsKey(it) }
            if (tokens.size < 2) continue

            val indexById = HashMap<String, Int>(tokens.size * 2)
            for (i in tokens.indices) {
                // Keep first occurrence index in case a loop route visits a terminal twice
                indexById.putIfAbsent(tokens[i], i)
            }

            val rIdx = parsedRoutes.size
            val category = BmtcBusCategory.fromRouteOrName(raw.routeNo)
            parsedRoutes.add(
                ParsedBusRoute(
                    id = raw.id,
                    routeNo = raw.routeNo,
                    origin = raw.origin,
                    destination = raw.destination,
                    stopIds = tokens,
                    stopIndexById = indexById,
                    category = category
                )
            )

            for (sid in indexById.keys) {
                inverted.getOrPut(sid) { ArrayList(8) }.add(rIdx)
            }
        }

        val compactMap = HashMap<String, IntArray>(inverted.size)
        for ((sid, list) in inverted) {
            compactMap[sid] = list.toIntArray()
        }

        return IndexedBmtcData(
            stopById = stopById,
            allStops = stops,
            routes = parsedRoutes,
            stopToRouteIndices = compactMap
        )
    }

    fun BusStopEntity.toActiveStation(
        routeLabel: String,
        isInterchange: Boolean = false
    ): NammaMetroNetwork.ActiveStation {
        return NammaMetroNetwork.ActiveStation(
            id = id,
            name = name,
            line = routeLabel,
            latitude = latitude,
            longitude = longitude,
            isInterchange = isInterchange
        )
    }

    /**
     * Plans up to 5 best BMTC route options from [origin] to [destination] (`destinationName`).
     * Supports opposite-side road pole selection (`bPos < ePos`), direct buses, and 1-transfer hubs.
     */
    fun planBusJourneys(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String,
        index: IndexedBmtcData,
        preferredBoardingStop: BusStopEntity? = null
    ): List<BmtcRouteOption> {
        if (index.isEmpty) return emptyList()

        val boardingCandidates = findCandidateStops(
            point = origin,
            index = index,
            preferredStop = preferredBoardingStop,
            maxCount = 24
        )
        val exitCandidates = findCandidateStops(
            point = destination,
            index = index,
            preferredStop = null,
            maxCount = 24
        )

        if (boardingCandidates.isEmpty() || exitCandidates.isEmpty()) return emptyList()

        val exitDistanceMap = HashMap<String, Double>(exitCandidates.size * 2)
        for ((stop, dist) in exitCandidates) {
            exitDistanceMap[stop.id] = dist
        }

        // 1. Direct route search (deduplicated by base route number, e.g. "500-D")
        val bestDirectByRouteNo = LinkedHashMap<String, BmtcRouteOption>()

        for ((bStop, walkToBoard) in boardingCandidates) {
            val routeIndices = index.stopToRouteIndices[bStop.id] ?: continue
            for (rIdx in routeIndices) {
                val route = index.routes[rIdx]
                val bPos = route.stopIndexById[bStop.id] ?: continue

                var bestExitStop: BusStopEntity? = null
                var bestExitPos = -1
                var bestDirectScore = Float.MAX_VALUE

                for ((eStop, walkFromExit) in exitCandidates) {
                    if (eStop.id == bStop.id) continue
                    val ePos = route.stopIndexById[eStop.id] ?: continue
                    if (bPos >= ePos) continue

                    val stopsCount = ePos - bPos
                    val score = (walkToBoard * 2.0).toFloat() +
                        (walkFromExit * 2.0).toFloat() +
                        stopsCount * STOP_WEIGHT_METERS
                    if (score < bestDirectScore) {
                        bestDirectScore = score
                        bestExitStop = eStop
                        bestExitPos = ePos
                    }
                }

                if (bestExitStop != null && bestExitPos > bPos) {
                    val stopsList = route.stopIds.subList(bPos, bestExitPos + 1)
                        .mapNotNull { sid ->
                            index.stopById[sid]?.toActiveStation(
                                routeLabel = route.routeNo,
                                isInterchange = sid == bStop.id || sid == bestExitStop.id
                            )
                        }
                    if (stopsList.size < 2) continue

                    val exitWalkDist = (exitDistanceMap[bestExitStop.id] ?: 0.0).toFloat()
                    val dirText = if (route.destination.isNotBlank()) "Towards ${route.destination}" else "Towards ${bestExitStop.name}"
                    val leg = MetroLeg(
                        lineName = route.routeNo,
                        lineColorInt = route.category.primaryColorInt,
                        stations = stopsList,
                        directionLabel = dirText,
                        isBusLeg = true
                    )
                    val plan = MetroJourneyPlan(
                        boardingStation = stopsList.first(),
                        exitStation = stopsList.last(),
                        legs = listOf(leg),
                        totalStops = leg.stopsCount,
                        requiresLineChange = false,
                        interchangeStations = emptyList(),
                        destinationPinName = destinationName,
                        destinationPinPoint = destination,
                        exitWalkDistanceMeters = exitWalkDist,
                        isBusJourney = true
                    )
                    val option = BmtcRouteOption(
                        id = "direct_${route.id}_${bStop.id}_${bestExitStop.id}",
                        routeTitle = route.routeNo,
                        subtitle = "$dirText • ${leg.stopsCount} stop${if (leg.stopsCount == 1) "" else "s"}",
                        category = route.category,
                        plan = plan,
                        isTransferRoute = false,
                        scoreMeters = bestDirectScore
                    )

                    val familyKey = route.routeNo.trim().uppercase()
                    val existing = bestDirectByRouteNo[familyKey]
                    if (existing == null || option.scoreMeters < existing.scoreMeters) {
                        bestDirectByRouteNo[familyKey] = option
                    }
                }
            }
        }

        val results = bestDirectByRouteNo.values
            .sortedBy { it.scoreMeters }
            .take(5)
            .toMutableList()

        // 2. Automatic 1-transfer fallback when direct options are scarce (< 3)
        if (results.size < 3) {
            val transferOptions = findBestTransferJourneys(
                boardingCandidates = boardingCandidates.take(12),
                exitCandidates = exitCandidates.take(12),
                destination = destination,
                destinationName = destinationName,
                index = index
            )
            val needed = (5 - results.size).coerceAtLeast(0)
            for (opt in transferOptions) {
                if (results.size >= 5) break
                if (results.none { it.routeTitle.equals(opt.routeTitle, ignoreCase = true) }) {
                    results.add(opt)
                }
                if (results.size >= 5 || needed == 0) break
            }
        }

        // 3. Sparse / disconnected area fallback so bus mode never fails completely
        if (results.isEmpty()) {
            val closestBoard = boardingCandidates.firstOrNull()
            val closestExit = exitCandidates.firstOrNull()
            if (closestBoard != null && closestExit != null && closestBoard.first.id != closestExit.first.id) {
                val boardRouteTag = closestBoard.first.ref.orEmpty().split(',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "BMTC"
                val boardStation = closestBoard.first.toActiveStation(boardRouteTag)
                val exitStation = closestExit.first.toActiveStation(boardRouteTag)
                val leg = MetroLeg(
                    lineName = boardRouteTag,
                    lineColorInt = BmtcBusCategory.fromRouteOrName(boardRouteTag).primaryColorInt,
                    stations = listOf(boardStation, exitStation),
                    directionLabel = "Towards ${exitStation.name}",
                    isBusLeg = true
                )
                val plan = MetroJourneyPlan(
                    boardingStation = boardStation,
                    exitStation = exitStation,
                    legs = listOf(leg),
                    totalStops = 1,
                    requiresLineChange = false,
                    interchangeStations = emptyList(),
                    destinationPinName = destinationName,
                    destinationPinPoint = destination,
                    exitWalkDistanceMeters = closestExit.second.toFloat(),
                    isBusJourney = true
                )
                results.add(
                    BmtcRouteOption(
                        id = "fallback_${closestBoard.first.id}_${closestExit.first.id}",
                        routeTitle = boardRouteTag,
                        subtitle = "${closestBoard.first.name} ➔ ${closestExit.first.name}",
                        category = BmtcBusCategory.fromRouteOrName(boardRouteTag),
                        plan = plan,
                        isTransferRoute = false,
                        scoreMeters = (closestBoard.second + closestExit.second).toFloat() + 2000f
                    )
                )
            }
        }

        return results.sortedBy { it.scoreMeters }.take(5)
    }

    private data class FirstLegCandidate(
        val route: ParsedBusRoute,
        val boardStop: BusStopEntity,
        val boardPos: Int,
        val transferStop: BusStopEntity,
        val transferPos: Int,
        val walkToBoard: Double,
        val costMeters: Float
    )

    private fun findBestTransferJourneys(
        boardingCandidates: List<Pair<BusStopEntity, Double>>,
        exitCandidates: List<Pair<BusStopEntity, Double>>,
        destination: GeoPoint,
        destinationName: String,
        index: IndexedBmtcData
    ): List<BmtcRouteOption> {
        val bestByStopId = HashMap<String, FirstLegCandidate>(1024)
        val bestByGroupId = HashMap<String, FirstLegCandidate>(512)

        for ((bStop, walkToBoard) in boardingCandidates) {
            val routeIndices = index.stopToRouteIndices[bStop.id] ?: continue
            for (rIdx in routeIndices) {
                val route = index.routes[rIdx]
                val bPos = route.stopIndexById[bStop.id] ?: continue
                // Skip super long winding detours (> 60 stops) for first leg
                val maxT = (bPos + 60).coerceAtMost(route.stopIds.size - 1)
                for (tPos in (bPos + 1)..maxT) {
                    val tId = route.stopIds[tPos]
                    val tStop = index.stopById[tId] ?: continue
                    val cost = (walkToBoard * 2.0).toFloat() + (tPos - bPos) * STOP_WEIGHT_METERS
                    val candidate = FirstLegCandidate(route, bStop, bPos, tStop, tPos, walkToBoard, cost)

                    val prevExact = bestByStopId[tId]
                    if (prevExact == null || cost < prevExact.costMeters) {
                        bestByStopId[tId] = candidate
                    }
                    val gid = tStop.groupId.orEmpty().trim()
                    if (gid.isNotEmpty()) {
                        val prevGroup = bestByGroupId[gid]
                        if (prevGroup == null || cost < prevGroup.costMeters) {
                            bestByGroupId[gid] = candidate
                        }
                    }
                }
            }
        }

        if (bestByStopId.isEmpty()) return emptyList()

        val bestByPairKey = LinkedHashMap<String, BmtcRouteOption>()

        for ((eStop, walkFromExit) in exitCandidates) {
            val routeIndices = index.stopToRouteIndices[eStop.id] ?: continue
            for (r2Idx in routeIndices) {
                val r2 = index.routes[r2Idx]
                val ePos = r2.stopIndexById[eStop.id] ?: continue
                val minT = (ePos - 60).coerceAtLeast(0)
                for (t2Pos in (ePos - 1) downTo minT) {
                    val t2Id = r2.stopIds[t2Pos]
                    val t2Stop = index.stopById[t2Id] ?: continue
                    val first = bestByStopId[t2Id]
                        ?: t2Stop.groupId.orEmpty().trim().takeIf { it.isNotEmpty() }?.let { bestByGroupId[it] }
                        ?: continue

                    if (first.route.routeNo.equals(r2.routeNo, ignoreCase = true)) continue
                    if (first.boardStop.id == eStop.id) continue

                    val transferWalk = RoadRoutingRepository.haversineDistanceMeters(
                        first.transferStop.latitude, first.transferStop.longitude,
                        t2Stop.latitude, t2Stop.longitude
                    ).toFloat()
                    if (transferWalk > 250f) continue

                    val leg2StopsCount = ePos - t2Pos
                    val leg1StopsCount = first.transferPos - first.boardPos
                    val totalScore = first.costMeters +
                        (walkFromExit * 2.0).toFloat() +
                        leg2StopsCount * STOP_WEIGHT_METERS +
                        TRANSFER_PENALTY_METERS +
                        transferWalk * 2.0f

                    val pairKey = "${first.route.routeNo.uppercase()}->${r2.routeNo.uppercase()}"
                    val existing = bestByPairKey[pairKey]
                    if (existing != null && totalScore >= existing.scoreMeters) continue

                    val leg1Stations = first.route.stopIds.subList(first.boardPos, first.transferPos + 1)
                        .mapIndexedNotNull { idx, sid ->
                            index.stopById[sid]?.toActiveStation(
                                routeLabel = first.route.routeNo,
                                isInterchange = idx == leg1StopsCount
                            )
                        }
                    val leg2Stations = r2.stopIds.subList(t2Pos, ePos + 1)
                        .mapIndexedNotNull { idx, sid ->
                            index.stopById[sid]?.toActiveStation(
                                routeLabel = r2.routeNo,
                                isInterchange = idx == 0
                            )
                        }
                    if (leg1Stations.size < 2 || leg2Stations.size < 2) continue

                    val dir1 = if (first.route.destination.isNotBlank()) "Towards ${first.route.destination}" else "Towards ${first.transferStop.name}"
                    val dir2 = if (r2.destination.isNotBlank()) "Towards ${r2.destination}" else "Towards ${eStop.name}"
                    val leg1 = MetroLeg(
                        lineName = first.route.routeNo,
                        lineColorInt = first.route.category.primaryColorInt,
                        stations = leg1Stations,
                        directionLabel = dir1,
                        isBusLeg = true
                    )
                    val leg2 = MetroLeg(
                        lineName = r2.routeNo,
                        lineColorInt = r2.category.primaryColorInt,
                        stations = leg2Stations,
                        directionLabel = dir2,
                        isBusLeg = true
                    )
                    val totalStops = leg1.stopsCount + leg2.stopsCount
                    val plan = MetroJourneyPlan(
                        boardingStation = leg1Stations.first(),
                        exitStation = leg2Stations.last(),
                        legs = listOf(leg1, leg2),
                        totalStops = totalStops,
                        requiresLineChange = true,
                        interchangeStations = listOf(leg1Stations.last()),
                        destinationPinName = destinationName,
                        destinationPinPoint = destination,
                        exitWalkDistanceMeters = walkFromExit.toFloat(),
                        isBusJourney = true
                    )
                    bestByPairKey[pairKey] = BmtcRouteOption(
                        id = "xfer_${first.route.id}_${r2.id}",
                        routeTitle = "${first.route.routeNo} ➔ ${r2.routeNo}",
                        subtitle = "Change at ${first.transferStop.name} • $totalStops stops",
                        category = first.route.category,
                        plan = plan,
                        isTransferRoute = true,
                        scoreMeters = totalScore
                    )
                }
            }
        }

        return bestByPairKey.values.sortedBy { it.scoreMeters }.take(4)
    }

    private fun findCandidateStops(
        point: GeoPoint,
        index: IndexedBmtcData,
        preferredStop: BusStopEntity?,
        maxCount: Int
    ): List<Pair<BusStopEntity, Double>> {
        if (preferredStop != null) {
            val prefDist = RoadRoutingRepository.haversineDistanceMeters(
                point.latitude, point.longitude,
                preferredStop.latitude, preferredStop.longitude
            )
            // Also include opposite-side sibling pole at the same stop (<= 100m, same groupId or name)
            val siblings = index.allStops.mapNotNull { stop ->
                if (stop.id == preferredStop.id) return@mapNotNull null
                val sameGroup = !preferredStop.groupId.isNullOrBlank() && stop.groupId == preferredStop.groupId
                val sameName = stop.name.equals(preferredStop.name, ignoreCase = true)
                if (!sameGroup && !sameName) return@mapNotNull null
                val between = RoadRoutingRepository.haversineDistanceMeters(
                    preferredStop.latitude, preferredStop.longitude,
                    stop.latitude, stop.longitude
                )
                if (between <= 120.0) {
                    val distFromPoint = RoadRoutingRepository.haversineDistanceMeters(
                        point.latitude, point.longitude,
                        stop.latitude, stop.longitude
                    )
                    stop to distFromPoint
                } else null
            }
            return listOf(preferredStop to prefDist) + siblings.sortedBy { it.second }
        }

        val withinPrimary = ArrayList<Pair<BusStopEntity, Double>>(32)
        val withinFallback = ArrayList<Pair<BusStopEntity, Double>>(32)

        for (stop in index.allStops) {
            if (abs(stop.latitude - point.latitude) > 0.05 || abs(stop.longitude - point.longitude) > 0.05) {
                continue
            }
            val dist = RoadRoutingRepository.haversineDistanceMeters(
                point.latitude, point.longitude,
                stop.latitude, stop.longitude
            )
            if (dist <= CANDIDATE_RADIUS_METERS) {
                withinPrimary.add(stop to dist)
            } else if (dist <= FALLBACK_RADIUS_METERS) {
                withinFallback.add(stop to dist)
            }
        }

        return if (withinPrimary.isNotEmpty()) {
            withinPrimary.sortedBy { it.second }.take(maxCount)
        } else {
            withinFallback.sortedBy { it.second }.take(maxCount)
        }
    }
}
