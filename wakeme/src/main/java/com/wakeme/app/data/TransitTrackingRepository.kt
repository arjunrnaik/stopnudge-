package com.wakeme.app.data

import com.wakeme.app.model.DestinationConfig
import com.wakeme.app.service.MetroJourneyStage
import kotlinx.coroutines.flow.MutableStateFlow
import org.osmdroid.util.GeoPoint

/**
 * Centralized state holder for live trip telemetry and multi-stage transit tracking.
 * Decouples UI/ViewModel observation from [com.wakeme.app.service.WakeMeTrackingService] internals.
 */
object TransitTrackingRepository {
    val liveDistanceMeters = MutableStateFlow<Float?>(null)
    val straightLineDistanceMeters = MutableStateFlow<Float?>(null)
    val currentSpeedKmh = MutableStateFlow<Float?>(null)
    val estimatedEtaMinutes = MutableStateFlow<Int?>(null)
    val initialDistanceMeters = MutableStateFlow<Float?>(null)
    val routePolyline = MutableStateFlow<List<GeoPoint>>(emptyList())
    val alternativeRoutes = MutableStateFlow<List<RouteCandidate>>(emptyList())
    val allCandidateRoutes = MutableStateFlow<List<RouteCandidate>>(emptyList())
    val selectedRouteId = MutableStateFlow<String?>("fastest")
    val isRerouting = MutableStateFlow(false)
    val isRoadRouteActive = MutableStateFlow(false)
    val currentBearing = MutableStateFlow<Float?>(null)
    val currentLocationPoint = MutableStateFlow<GeoPoint?>(null)
    val isTracking = MutableStateFlow(false)
    val activeConfig = MutableStateFlow<DestinationConfig?>(null)

    val metroStage = MutableStateFlow(MetroJourneyStage.NONE)
    val activeMetroPlan = MutableStateFlow<MetroJourneyPlan?>(null)
    val currentMetroLegIndex = MutableStateFlow(0)
    val metroStopsRemaining = MutableStateFlow<Int?>(null)
    val metroCurrentStationName = MutableStateFlow<String?>(null)
    val metroNextStationName = MutableStateFlow<String?>(null)
    val metroAlertStationName = MutableStateFlow<String?>(null)
    val metroInterchangePrompt = MutableStateFlow<String?>(null)
    val preloadedBoardingRoadPoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val preloadedExitRoadPoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val preloadedRoadRoutePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val preloadedBoardingRoadDistanceMeters = MutableStateFlow<Float?>(null)
    val preloadedBoardingRoadDurationSeconds = MutableStateFlow<Float?>(null)
    val preloadedExitRoadDistanceMeters = MutableStateFlow<Float?>(null)
    val preloadedExitRoadDurationSeconds = MutableStateFlow<Float?>(null)
    val preloadedBusLegRoadPoints = MutableStateFlow<Map<Int, List<GeoPoint>>>(emptyMap())
}
