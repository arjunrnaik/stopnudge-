package com.wakeme.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import com.google.android.gms.location.*
import com.wakeme.app.MainActivity
import com.wakeme.app.WakeMeApp
import com.wakeme.app.alarm.AlarmDismissActivity
import com.wakeme.app.alarm.WakeMeAlarmController
import com.wakeme.app.data.MetroJourneyPlan
import com.wakeme.app.data.MetroRoutePlanner
import com.wakeme.app.data.RoadRoutingRepository
import com.wakeme.app.data.RouteCandidate
import com.wakeme.app.data.TransitTrackingRepository
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.model.AlertMode
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.model.DestinationConfig
import com.wakeme.app.model.VehicleMode

enum class MetroJourneyStage {
    NONE,
    TO_BOARDING_ROAD,
    ON_METRO_LEG,
    INTERCHANGE_WAIT,
    TO_FINAL_DESTINATION_ROAD
}

class WakeMeTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val locationThread = HandlerThread("WakeMeLocationThread")
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var destinationConfig: DestinationConfig? = null
    private var lastDistanceMeters: Float = Float.MAX_VALUE
    private var consecutiveOffRouteCount = 0

    override fun onCreate() {
        super.onCreate()
        locationThread.start()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_STOP_TRACKING -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_TRACKING -> {
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Destination"
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                val radius = intent.getFloatExtra(EXTRA_RADIUS, 800f)
                val vehicleMode = intent.getStringExtra(EXTRA_VEHICLE_MODE)?.let { VehicleMode.valueOf(it) } ?: VehicleMode.METRO
                val mode = intent.getStringExtra(EXTRA_MODE)?.let { AlertMode.valueOf(it) } ?: AlertMode.WAKEUP_ALARM
                val routing = intent.getStringExtra(EXTRA_ROUTING)?.let { AudioRouting.valueOf(it) } ?: AudioRouting.BOTH
                val note = intent.getStringExtra(EXTRA_NOTE) ?: ""
                val metroStopsBefore = intent.getIntExtra(EXTRA_METRO_STOPS_BEFORE, 1)
                val boardingLat = intent.getDoubleExtra(EXTRA_METRO_BOARDING_LAT, Double.NaN)
                val boardingLon = intent.getDoubleExtra(EXTRA_METRO_BOARDING_LON, Double.NaN)
                val boardingName = intent.getStringExtra(EXTRA_METRO_BOARDING_NAME)

                destinationConfig = DestinationConfig(
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    radiusMeters = radius,
                    vehicleMode = vehicleMode,
                    alertMode = mode,
                    audioRouting = routing,
                    reminderNote = note,
                    metroStopsBeforeAlert = metroStopsBefore,
                    metroBoardingLat = if (boardingLat.isNaN()) null else boardingLat,
                    metroBoardingLon = if (boardingLon.isNaN()) null else boardingLon,
                    metroBoardingName = boardingName
                )
                hasAlertFired.set(false)
                hasInterchangeAlertFiredForLeg.set(-1)
                isTracking.value = true
                activeConfig.value = destinationConfig
                initialDistanceMeters.value = null
                currentSpeedKmh.value = null
                estimatedEtaMinutes.value = null
                routePolyline.value = emptyList()
                alternativeRoutes.value = emptyList()
                allCandidateRoutes.value = emptyList()
                selectedRouteId.value = "fastest"
                isRerouting.value = false
                consecutiveOffRouteCount = 0
                isRoadRouteActive.value = false
                currentBearing.value = null
                currentLocationPoint.value = null
                lastRouteFetchTime = 0L
                matchedPolylineSegmentIndex = 0
                matchedMetroStationIndex = 0
                currentMetroLegIndex.value = 0
                metroStopsRemaining.value = null
                metroCurrentStationName.value = null
                metroNextStationName.value = null
                metroAlertStationName.value = null
                metroInterchangePrompt.value = null

                if ((vehicleMode == VehicleMode.METRO || vehicleMode == VehicleMode.BUS) && activeMetroPlan.value != null) {
                    val bPts = preloadedBoardingRoadPoints.value
                    if (bPts.size >= 2) {
                        metroStage.value = MetroJourneyStage.TO_BOARDING_ROAD
                        routePolyline.value = bPts
                        isRoadRouteActive.value = true
                    } else {
                        metroStage.value = MetroJourneyStage.NONE
                        routePolyline.value = emptyList()
                    }
                } else if (vehicleMode.usesRoadDistanceTrigger && preloadedRoadRoutePoints.value.size >= 2) {
                    metroStage.value = MetroJourneyStage.NONE
                    routePolyline.value = preloadedRoadRoutePoints.value
                    isRoadRouteActive.value = true
                } else {
                    metroStage.value = MetroJourneyStage.NONE
                    activeMetroPlan.value = null
                    routePolyline.value = emptyList()
                }

                startForegroundNotification(name, "Initiating hardware GPS tracking...")
                startLocationUpdates()
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification(title: String, statusText: String) {
        val notification = buildTrackingNotification(title, statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildTrackingNotification(title: String, statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeMeTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WakeMeApp.TRACKING_CHANNEL_ID)
            .setContentTitle("WakeMe: $title")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val config = destinationConfig ?: return
        val notification = buildTrackingNotification(config.name, statusText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            updateNotification("Location permission missing — tracking stopped")
            stopTracking()
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
            .setMinUpdateIntervalMillis(1500L)
            .setMinUpdateDistanceMeters(3f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onNewLocation(location)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                locationThread.looper
            )
        } catch (_: SecurityException) {
            updateNotification("Location permission revoked — tracking stopped")
            stopTracking()
            stopSelf()
        } catch (_: Exception) {}
    }

    private fun onNewLocation(currentLocation: Location) {
        val config = destinationConfig ?: return

        // 1. Hardware straight-line distance for micro-geofence trigger
        val straightDist = haversineDistanceMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            config.latitude,
            config.longitude
        ).toFloat()

        straightLineDistanceMeters.value = straightDist
        lastDistanceMeters = straightDist

        // 2. Telemetry: Location, Bearing & Speed
        currentLocationPoint.value = GeoPoint(currentLocation.latitude, currentLocation.longitude)
        if (currentLocation.hasBearing()) {
            currentBearing.value = currentLocation.bearing
        }

        val speedMps = if (currentLocation.hasSpeed()) currentLocation.speed else 0f
        val speedKmh = speedMps * 3.6f
        currentSpeedKmh.value = speedKmh

        if (config.vehicleMode == VehicleMode.METRO || config.vehicleMode == VehicleMode.BUS) {
            handleMetroLocationUpdate(currentLocation, config, speedMps)
            return
        }

        // Driving / Cab / Auto Road Trigger
        updateRoadRouteTelemetry(currentLocation, config.latitude, config.longitude, straightDist, speedMps)

        // Arrival Trigger Evaluation
        val triggerDecision = RoadRoutingRepository.evaluateArrivalTrigger(
            vehicleMode = config.vehicleMode,
            targetThresholdMeters = config.radiusMeters,
            straightLineMeters = straightDist,
            roadDistanceMeters = liveDistanceMeters.value,
            hasActiveRoadRoute = isRoadRouteActive.value || routePolyline.value.size >= 2
        )

        if (triggerDecision.shouldTrigger) {
            if (hasAlertFired.compareAndSet(false, true)) {
                triggerArrivalAlert(config, triggerDecision.effectiveDistanceMeters)
            }
        } else {
            val dist = liveDistanceMeters.value ?: (straightDist * 1.28f)
            val distText = if (dist >= 1000f) {
                String.format("%.1f km remaining", dist / 1000f)
            } else {
                "${dist.toInt()}m remaining"
            }
            val etaText = estimatedEtaMinutes.value?.let { " • ~$it min" } ?: ""
            updateNotification("$distText$etaText")
        }
    }

    private fun updateRoadRouteTelemetry(
        currentLocation: Location,
        targetLat: Double,
        targetLon: Double,
        straightDist: Float,
        speedMps: Float
    ) {
        val currentPoly = routePolyline.value
        var triggeredOffRouteReroute = false
        if (currentPoly.size >= 2) {
            val polyProgress = RoadRoutingRepository.computeRemainingRoadDistanceAlongPolyline(
                lat = currentLocation.latitude,
                lon = currentLocation.longitude,
                polyline = currentPoly,
                minSegmentIndex = matchedPolylineSegmentIndex
            )
            if (polyProgress != null && polyProgress.offRouteMeters <= 55.0) {
                matchedPolylineSegmentIndex = polyProgress.matchedSegmentIndex
                liveDistanceMeters.value = polyProgress.remainingMeters
                consecutiveOffRouteCount = 0
                if (speedMps > 1.0f) {
                    val etaSec = (polyProgress.remainingMeters / speedMps).toInt()
                    estimatedEtaMinutes.value = (etaSec / 60).coerceAtLeast(1)
                }
            } else if (!isRerouting.value && polyProgress != null && polyProgress.offRouteMeters > 55.0 && speedMps > 0.8f) {
                consecutiveOffRouteCount++
                if (consecutiveOffRouteCount >= 2) {
                    consecutiveOffRouteCount = 0
                    triggeredOffRouteReroute = true
                    isRerouting.value = true
                    serviceScope.launch {
                        try {
                            val route = RoadRoutingRepository.getRoute(
                                currentLocation.latitude,
                                currentLocation.longitude,
                                targetLat,
                                targetLon
                            )
                            val allRoutes = listOf(route.fastestRoute) + route.alternativeRoutes
                            allCandidateRoutes.value = allRoutes

                            val currentSelId = selectedRouteId.value
                            val activeRoute = allRoutes.find { it.id == currentSelId } ?: route.fastestRoute
                            selectedRouteId.value = activeRoute.id
                            matchedPolylineSegmentIndex = 0
                            routePolyline.value = activeRoute.points
                            alternativeRoutes.value = allRoutes.filter { it.id != activeRoute.id }
                            liveDistanceMeters.value = activeRoute.distanceMeters
                            isRoadRouteActive.value = route.isRoadRoute
                            lastRouteFetchTime = System.currentTimeMillis()

                            if (speedMps > 1.0f) {
                                val etaSec = (activeRoute.distanceMeters / speedMps).toInt()
                                val etaMin = (etaSec / 60).coerceAtLeast(1)
                                estimatedEtaMinutes.value = etaMin
                            }
                        } finally {
                            isRerouting.value = false
                        }
                    }
                }
            } else {
                consecutiveOffRouteCount = 0
            }
        }

        val now = System.currentTimeMillis()
        val estRemainingRoad = liveDistanceMeters.value ?: (straightDist * 1.28f)
        val refreshIntervalMs = if (estRemainingRoad <= 2500f) 8_000L else 20_000L
        if (!triggeredOffRouteReroute && (now - lastRouteFetchTime > refreshIntervalMs || liveDistanceMeters.value == null)) {
            lastRouteFetchTime = now
            serviceScope.launch {
                val route = RoadRoutingRepository.getRoute(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    targetLat,
                    targetLon
                )
                val allRoutes = listOf(route.fastestRoute) + route.alternativeRoutes
                allCandidateRoutes.value = allRoutes

                val currentSelId = selectedRouteId.value
                val activeRoute = allRoutes.find { it.id == currentSelId } ?: route.fastestRoute
                selectedRouteId.value = activeRoute.id
                matchedPolylineSegmentIndex = 0
                routePolyline.value = activeRoute.points
                alternativeRoutes.value = allRoutes.filter { it.id != activeRoute.id }
                liveDistanceMeters.value = activeRoute.distanceMeters
                isRoadRouteActive.value = route.isRoadRoute

                if (initialDistanceMeters.value == null || initialDistanceMeters.value == 0f) {
                    initialDistanceMeters.value = activeRoute.distanceMeters
                }

                if (speedMps > 1.0f) {
                    val etaSec = (activeRoute.distanceMeters / speedMps).toInt()
                    val etaMin = (etaSec / 60).coerceAtLeast(1)
                    estimatedEtaMinutes.value = etaMin
                }
            }
        } else if (!triggeredOffRouteReroute) {
            if (liveDistanceMeters.value == null) {
                liveDistanceMeters.value = straightDist * 1.28f
            }
            if (speedMps > 1.0f && liveDistanceMeters.value != null) {
                val etaSec = (liveDistanceMeters.value!! / speedMps).toInt()
                val etaMin = (etaSec / 60).coerceAtLeast(1)
                estimatedEtaMinutes.value = etaMin
            }
        }
    }

    private fun handleMetroLocationUpdate(
        currentLocation: Location,
        config: DestinationConfig,
        speedMps: Float
    ) {
        val currentGeo = GeoPoint(currentLocation.latitude, currentLocation.longitude)
        val isBusMode = config.vehicleMode == VehicleMode.BUS
        var plan = activeMetroPlan.value
        if (plan == null) {
            if (isBusMode) {
                val bStop = NammaMetroNetwork.ActiveStation(
                    id = "bus_board_fallback",
                    name = config.metroBoardingName ?: "Nearest Bus Stop",
                    line = "BMTC",
                    latitude = config.metroBoardingLat ?: currentGeo.latitude,
                    longitude = config.metroBoardingLon ?: currentGeo.longitude
                )
                val eStop = NammaMetroNetwork.ActiveStation(
                    id = "bus_exit_fallback",
                    name = config.name,
                    line = "BMTC",
                    latitude = config.latitude,
                    longitude = config.longitude
                )
                val leg = com.wakeme.app.data.MetroLeg(
                    lineName = "BMTC",
                    lineColorInt = 0xFF1E88E5.toInt(),
                    stations = listOf(bStop, eStop),
                    directionLabel = "Towards ${config.name}",
                    isBusLeg = true
                )
                plan = com.wakeme.app.data.MetroJourneyPlan(
                    boardingStation = bStop,
                    exitStation = eStop,
                    legs = listOf(leg),
                    totalStops = 1,
                    requiresLineChange = false,
                    interchangeStations = emptyList(),
                    destinationPinName = config.name,
                    destinationPinPoint = GeoPoint(config.latitude, config.longitude),
                    exitWalkDistanceMeters = 0f,
                    isBusJourney = true
                )
            } else {
                val boardingStation = if (config.metroBoardingLat != null && config.metroBoardingLon != null) {
                    NammaMetroNetwork.findNearestActiveStation(GeoPoint(config.metroBoardingLat, config.metroBoardingLon)).first
                } else {
                    NammaMetroNetwork.findNearestActiveStation(currentGeo).first
                }
                val exitStation = NammaMetroNetwork.findNearestActiveStation(GeoPoint(config.latitude, config.longitude)).first
                plan = MetroRoutePlanner.planJourney(
                    boardingStation = boardingStation,
                    exitStation = exitStation,
                    destinationPinName = config.name,
                    destinationPinPoint = GeoPoint(config.latitude, config.longitude)
                )
            }
            activeMetroPlan.value = plan
        }

        val transitLabel = if (plan.isBusJourney || isBusMode) "Bus Stop" else "Metro"
        val modeIcon = if (plan.isBusJourney || isBusMode) "🚌" else "🚇"

        // Ensure solid Stage 3 exit road path is always loaded for Active Trip map even on Stage 1/2
        if (plan.exitWalkDistanceMeters > MetroRoutePlanner.BOARDING_PROXIMITY_METERS &&
            preloadedExitRoadPoints.value.isEmpty() && !isFetchingExitPreview
        ) {
            isFetchingExitPreview = true
            val exitSt = plan.exitStation
            serviceScope.launch {
                try {
                    val exitRoute = RoadRoutingRepository.getRoute(
                        exitSt.latitude, exitSt.longitude,
                        config.latitude, config.longitude
                    )
                    preloadedExitRoadPoints.value = exitRoute.points
                    preloadedExitRoadDistanceMeters.value = exitRoute.distanceMeters
                    preloadedExitRoadDurationSeconds.value = exitRoute.durationSeconds
                } catch (_: Exception) {
                } finally {
                    isFetchingExitPreview = false
                }
            }
        }

        val distToBoarding = haversineDistanceMeters(
            currentLocation.latitude, currentLocation.longitude,
            plan.boardingStation.latitude, plan.boardingStation.longitude
        ).toFloat()

        // Initialize stage on first fix: 50m boarding check
        if (metroStage.value == MetroJourneyStage.NONE) {
            if (distToBoarding <= MetroRoutePlanner.BOARDING_PROXIMITY_METERS) {
                enterMetroRideStage(plan, 0)
            } else {
                metroStage.value = MetroJourneyStage.TO_BOARDING_ROAD
            }
        }

        when (metroStage.value) {
            MetroJourneyStage.TO_BOARDING_ROAD -> {
                if (distToBoarding <= MetroRoutePlanner.BOARDING_PROXIMITY_METERS) {
                    enterMetroRideStage(plan, 0)
                    postBoardingReachedNotification(plan.boardingStation.name, plan.exitStation.name, plan.isBusJourney || isBusMode)
                } else {
                    updateRoadRouteTelemetry(
                        currentLocation,
                        plan.boardingStation.latitude,
                        plan.boardingStation.longitude,
                        distToBoarding,
                        speedMps
                    )
                    val dist = liveDistanceMeters.value ?: (distToBoarding * 1.28f)
                    val distText = if (dist >= 1000f) String.format("%.1f km", dist / 1000f) else "${dist.toInt()}m"
                    updateNotification("Go to ${plan.boardingStation.name} $transitLabel: $distText")
                }
            }

            MetroJourneyStage.INTERCHANGE_WAIT -> {
                val nextIdx = currentMetroLegIndex.value + 1
                val nextLeg = plan.legs.getOrNull(nextIdx)
                if (nextLeg != null && nextLeg.stations.size >= 2) {
                    val interchange = nextLeg.stations[0]
                    val nextSt = nextLeg.stations[1]
                    val distInterchange = haversineDistanceMeters(
                        currentLocation.latitude, currentLocation.longitude,
                        interchange.latitude, interchange.longitude
                    )
                    val distNext = haversineDistanceMeters(
                        currentLocation.latitude, currentLocation.longitude,
                        nextSt.latitude, nextSt.longitude
                    )
                    // Auto-advance once user departs interchange platform/stop along the next leg
                    if (distInterchange > 150.0 && distNext < distInterchange) {
                        WakeMeAlarmController.stopAlert(this)
                        hasAlertFired.set(false)
                        metroInterchangePrompt.value = null
                        enterMetroRideStage(plan, nextIdx)
                    }
                }
            }

            MetroJourneyStage.ON_METRO_LEG -> {
                val legIdx = currentMetroLegIndex.value
                val leg = plan.legs.getOrNull(legIdx) ?: return
                val isInterchangeLeg = legIdx < plan.legs.size - 1
                val effectiveStopsBefore = if (isInterchangeLeg) {
                    config.metroStopsBeforeAlert.coerceIn(1, leg.stopsCount.coerceAtLeast(1))
                } else {
                    config.metroStopsBeforeAlert.coerceIn(0, leg.stopsCount)
                }

                val progress = MetroRoutePlanner.evaluateMetroLegProgress(
                    lat = currentLocation.latitude,
                    lon = currentLocation.longitude,
                    leg = leg,
                    stopsBeforeAlert = effectiveStopsBefore,
                    minStationIndex = matchedMetroStationIndex
                )

                matchedMetroStationIndex = progress.currentStationIndex
                metroStopsRemaining.value = progress.stopsRemaining
                metroCurrentStationName.value = progress.currentStation.name
                metroNextStationName.value = progress.nextStation?.name ?: leg.alightingStation.name
                metroAlertStationName.value = progress.alertStopStation.name
                liveDistanceMeters.value = progress.distanceToEndMeters

                if (initialDistanceMeters.value == null || initialDistanceMeters.value == 0f) {
                    initialDistanceMeters.value = progress.distanceToEndMeters.coerceAtLeast(1f)
                }
                if (speedMps > 1.0f) {
                    estimatedEtaMinutes.value = ((progress.distanceToEndMeters / speedMps) / 60).toInt().coerceAtLeast(1)
                }

                val nextName = progress.nextStation?.name ?: leg.alightingStation.name
                updateNotification("$modeIcon ${leg.lineName}: ${progress.stopsRemaining} stops left (Next: $nextName)")

                if (progress.shouldTriggerStopAlert) {
                    if (isInterchangeLeg) {
                        val nextLeg = plan.legs[legIdx + 1]
                        val prevLeg = hasInterchangeAlertFiredForLeg.get()
                        if (prevLeg != legIdx && hasInterchangeAlertFiredForLeg.compareAndSet(prevLeg, legIdx)) {
                            val interchangeName = leg.alightingStation.name
                            val nextLegDesc = if (nextLeg.isBusLeg) "Bus ${nextLeg.lineName}" else "${nextLeg.lineName} Line"
                            val prompt = "Change at $interchangeName ➔ $nextLegDesc (${nextLeg.directionLabel})"
                            metroInterchangePrompt.value = prompt
                            triggerInterchangeAlert(config, interchangeName, prompt)
                        }
                        if (progress.distanceToEndMeters <= 150f) {
                            metroStage.value = MetroJourneyStage.INTERCHANGE_WAIT
                        }
                    } else {
                        if (hasAlertFired.compareAndSet(false, true)) {
                            val targetLabel = if (effectiveStopsBefore == 0) {
                                "${leg.alightingStation.name} $transitLabel"
                            } else {
                                "${leg.alightingStation.name} ($effectiveStopsBefore stop${if (effectiveStopsBefore > 1) "s" else ""} away)"
                            }
                            triggerArrivalAlert(config.copy(name = targetLabel), progress.distanceToEndMeters)
                        }

                        // Auto-transition to Stage 3 once commuter arrives at / steps outside exit station/stop
                        val hasFinalRoadLeg = plan.exitWalkDistanceMeters > MetroRoutePlanner.BOARDING_PROXIMITY_METERS
                        val distToFinalDest = haversineDistanceMeters(
                            currentLocation.latitude, currentLocation.longitude,
                            config.latitude, config.longitude
                        ).toFloat()
                        if (hasAlertFired.get() && hasFinalRoadLeg &&
                            (progress.distanceToEndMeters <= 70f ||
                                (progress.distanceToEndMeters > 100f && distToFinalDest < plan.exitWalkDistanceMeters))
                        ) {
                            enterFinalDestinationRoadStage()
                            postExitedMetroNotification(plan.exitStation.name, plan.destinationPinName, plan.isBusJourney || isBusMode)
                        }
                    }
                }
            }

            MetroJourneyStage.TO_FINAL_DESTINATION_ROAD -> {
                val distToFinalDest = haversineDistanceMeters(
                    currentLocation.latitude, currentLocation.longitude,
                    config.latitude, config.longitude
                ).toFloat()

                updateRoadRouteTelemetry(
                    currentLocation,
                    config.latitude,
                    config.longitude,
                    distToFinalDest,
                    speedMps
                )

                val finalRoadDecision = RoadRoutingRepository.evaluateArrivalTrigger(
                    vehicleMode = VehicleMode.CAR,
                    targetThresholdMeters = 150f,
                    straightLineMeters = distToFinalDest,
                    roadDistanceMeters = liveDistanceMeters.value,
                    hasActiveRoadRoute = isRoadRouteActive.value || routePolyline.value.size >= 2,
                    microArrivalGateMeters = 80f
                )

                if (finalRoadDecision.shouldTrigger) {
                    if (hasAlertFired.compareAndSet(false, true)) {
                        triggerArrivalAlert(
                            config.copy(
                                alertMode = AlertMode.GENTLE_REMINDER,
                                vehicleMode = VehicleMode.CAR
                            ),
                            finalRoadDecision.effectiveDistanceMeters
                        )
                    }
                } else {
                    val dist = liveDistanceMeters.value ?: (distToFinalDest * 1.28f)
                    val distText = if (dist >= 1000f) String.format("%.1f km", dist / 1000f) else "${dist.toInt()}m"
                    updateNotification("Go to ${config.name}: $distText by road")
                }
            }

            MetroJourneyStage.NONE -> Unit
        }
    }

    private fun postExitedMetroNotification(exitName: String, destName: String, isBus: Boolean = false) {
        val label = if (isBus) "Bus Stop" else "Metro"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, WakeMeApp.GENTLE_CHANNEL_ID)
            .setContentTitle("Reached $exitName $label")
            .setContentText("Stage 3: Follow the road path to $destName.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ARRIVAL_ID, notif)
    }

    private fun postBoardingReachedNotification(boardingName: String, exitName: String, isBus: Boolean = false) {
        val label = if (isBus) "Bus Stop" else "Metro"
        val vehicleWord = if (isBus) "bus" else "train"
        val trackWord = if (isBus) "BMTC stops" else "Metro stops"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, WakeMeApp.GENTLE_CHANNEL_ID)
            .setContentTitle("Reached $boardingName $label")
            .setContentText("Board the $vehicleWord! Now tracking $trackWord to $exitName.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ARRIVAL_ID, notif)
    }

    private fun triggerInterchangeAlert(config: DestinationConfig, interchangeName: String, prompt: String) {
        WakeMeAlarmController.triggerAlert(applicationContext, config)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, WakeMeApp.GENTLE_CHANNEL_ID)
            .setContentTitle("🔄 Line Change Ahead: $interchangeName")
            .setContentText(prompt)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ARRIVAL_ID, notif)
        updateNotification("🔄 $prompt")
    }

    private fun triggerArrivalAlert(config: DestinationConfig, finalDistance: Float) {
        val safeName = config.name.take(120)
        val safeNote = config.reminderNote.take(240)
        val safeConfig = config.copy(name = safeName, reminderNote = safeNote)

        // 1. Trigger audio & vibration engine
        WakeMeAlarmController.triggerAlert(applicationContext, safeConfig)

        // 2. Alert mode dispatch
        if (safeConfig.alertMode == AlertMode.WAKEUP_ALARM) {
            val dismissIntent = Intent(this, AlarmDismissActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AlarmDismissActivity.EXTRA_DESTINATION_NAME, safeName)
                putExtra(AlarmDismissActivity.EXTRA_REMINDER_NOTE, safeNote)
                putExtra(AlarmDismissActivity.EXTRA_RADIUS_METERS, safeConfig.radiusMeters)
                putExtra(AlarmDismissActivity.EXTRA_IS_INTERCHANGE, false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canUseFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                notificationManager.canUseFullScreenIntent()

            if (canUseFullScreen) {
                try {
                    startActivity(dismissIntent)
                } catch (_: Exception) {
                    postAlarmFallbackNotification(safeConfig, dismissIntent)
                }
            } else {
                postAlarmFallbackNotification(safeConfig, dismissIntent)
            }
        } else {
            // Gentle Reminder: Post heads-up notification
            postGentleReminderNotification(safeConfig)
        }

        val distUnit = if (safeConfig.vehicleMode.usesRoadDistanceTrigger) "m along road" else "m"
        updateNotification("Arrived at destination (${finalDistance.toInt()}$distUnit)")
    }

    private fun postAlarmFallbackNotification(config: DestinationConfig, dismissIntent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, WakeMeApp.ALARM_CHANNEL_ID)
            .setContentTitle("Wake Up! Approaching ${config.name}")
            .setContentText(config.reminderNote.ifBlank { "Tap to dismiss your destination alarm." })
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ARRIVAL_ID, notif)
    }

    private fun postGentleReminderNotification(config: DestinationConfig) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultText = if (config.vehicleMode.usesRoadDistanceTrigger) {
            "Approaching destination along your road route."
        } else {
            "You have reached your destination geofence."
        }
        val notif = NotificationCompat.Builder(this, WakeMeApp.GENTLE_CHANNEL_ID)
            .setContentTitle("Arrived at ${config.name}")
            .setContentText(if (config.reminderNote.isNotEmpty()) config.reminderNote else defaultText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ARRIVAL_ID, notif)
    }

    private fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        WakeMeAlarmController.cancelSnooze()
        WakeMeAlarmController.stopAlert(this)
        hasAlertFired.set(false)
        hasInterchangeAlertFiredForLeg.set(-1)
        isTracking.value = false
        liveDistanceMeters.value = null
        straightLineDistanceMeters.value = null
        currentSpeedKmh.value = null
        estimatedEtaMinutes.value = null
        initialDistanceMeters.value = null
        routePolyline.value = emptyList()
        alternativeRoutes.value = emptyList()
        allCandidateRoutes.value = emptyList()
        selectedRouteId.value = null
        isRerouting.value = false
        consecutiveOffRouteCount = 0
        isRoadRouteActive.value = false
        currentBearing.value = null
        currentLocationPoint.value = null
        activeConfig.value = null
        matchedPolylineSegmentIndex = 0
        matchedMetroStationIndex = 0
        metroStage.value = MetroJourneyStage.NONE
        activeMetroPlan.value = null
        currentMetroLegIndex.value = 0
        metroStopsRemaining.value = null
        metroCurrentStationName.value = null
        metroNextStationName.value = null
        metroAlertStationName.value = null
        metroInterchangePrompt.value = null
        preloadedBoardingRoadPoints.value = emptyList()
        preloadedExitRoadPoints.value = emptyList()
        preloadedRoadRoutePoints.value = emptyList()
        preloadedBoardingRoadDistanceMeters.value = null
        preloadedBoardingRoadDurationSeconds.value = null
        preloadedExitRoadDistanceMeters.value = null
        preloadedExitRoadDurationSeconds.value = null
        preloadedBusLegRoadPoints.value = emptyMap()
        isFetchingExitPreview = false
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        locationThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_ARRIVAL_ID = 2002

        const val ACTION_START_TRACKING = "com.wakeme.app.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.wakeme.app.action.STOP_TRACKING"

        const val EXTRA_NAME = "extra_name"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_RADIUS = "extra_radius"
        const val EXTRA_VEHICLE_MODE = "extra_vehicle_mode"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_ROUTING = "extra_routing"
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_METRO_STOPS_BEFORE = "extra_metro_stops_before"
        const val EXTRA_METRO_BOARDING_LAT = "extra_metro_boarding_lat"
        const val EXTRA_METRO_BOARDING_LON = "extra_metro_boarding_lon"
        const val EXTRA_METRO_BOARDING_NAME = "extra_metro_boarding_name"

        val liveDistanceMeters get() = TransitTrackingRepository.liveDistanceMeters
        val straightLineDistanceMeters get() = TransitTrackingRepository.straightLineDistanceMeters
        val currentSpeedKmh get() = TransitTrackingRepository.currentSpeedKmh
        val estimatedEtaMinutes get() = TransitTrackingRepository.estimatedEtaMinutes
        val initialDistanceMeters get() = TransitTrackingRepository.initialDistanceMeters
        val routePolyline get() = TransitTrackingRepository.routePolyline
        val alternativeRoutes get() = TransitTrackingRepository.alternativeRoutes
        val allCandidateRoutes get() = TransitTrackingRepository.allCandidateRoutes
        val selectedRouteId get() = TransitTrackingRepository.selectedRouteId
        val isRerouting get() = TransitTrackingRepository.isRerouting
        val isRoadRouteActive get() = TransitTrackingRepository.isRoadRouteActive
        val currentBearing get() = TransitTrackingRepository.currentBearing
        val currentLocationPoint get() = TransitTrackingRepository.currentLocationPoint
        val isTracking get() = TransitTrackingRepository.isTracking
        val activeConfig get() = TransitTrackingRepository.activeConfig

        val metroStage get() = TransitTrackingRepository.metroStage
        val activeMetroPlan get() = TransitTrackingRepository.activeMetroPlan
        val currentMetroLegIndex get() = TransitTrackingRepository.currentMetroLegIndex
        val metroStopsRemaining get() = TransitTrackingRepository.metroStopsRemaining
        val metroCurrentStationName get() = TransitTrackingRepository.metroCurrentStationName
        val metroNextStationName get() = TransitTrackingRepository.metroNextStationName
        val metroAlertStationName get() = TransitTrackingRepository.metroAlertStationName
        val metroInterchangePrompt get() = TransitTrackingRepository.metroInterchangePrompt
        val preloadedBoardingRoadPoints get() = TransitTrackingRepository.preloadedBoardingRoadPoints
        val preloadedExitRoadPoints get() = TransitTrackingRepository.preloadedExitRoadPoints
        val preloadedRoadRoutePoints get() = TransitTrackingRepository.preloadedRoadRoutePoints
        val preloadedBoardingRoadDistanceMeters get() = TransitTrackingRepository.preloadedBoardingRoadDistanceMeters
        val preloadedBoardingRoadDurationSeconds get() = TransitTrackingRepository.preloadedBoardingRoadDurationSeconds
        val preloadedExitRoadDistanceMeters get() = TransitTrackingRepository.preloadedExitRoadDistanceMeters
        val preloadedExitRoadDurationSeconds get() = TransitTrackingRepository.preloadedExitRoadDurationSeconds
        val preloadedBusLegRoadPoints get() = TransitTrackingRepository.preloadedBusLegRoadPoints

        @Volatile
        private var matchedPolylineSegmentIndex = 0
        @Volatile
        private var matchedMetroStationIndex = 0
        private val hasAlertFired = AtomicBoolean(false)
        private val hasInterchangeAlertFiredForLeg = AtomicInteger(-1)
        @Volatile
        private var lastRouteFetchTime = 0L
        @Volatile
        private var isFetchingExitPreview = false

        fun seedPreloadedMetroRoutes(
            plan: MetroJourneyPlan?,
            boardingPoints: List<GeoPoint>,
            boardingDistMeters: Float?,
            boardingDurSeconds: Float?,
            exitPoints: List<GeoPoint>,
            exitDistMeters: Float?,
            exitDurSeconds: Float?,
            busLegRoadPoints: Map<Int, List<GeoPoint>> = emptyMap()
        ) {
            activeMetroPlan.value = plan
            preloadedBoardingRoadPoints.value = boardingPoints
            preloadedBoardingRoadDistanceMeters.value = boardingDistMeters
            preloadedBoardingRoadDurationSeconds.value = boardingDurSeconds
            preloadedExitRoadPoints.value = exitPoints
            preloadedExitRoadDistanceMeters.value = exitDistMeters
            preloadedExitRoadDurationSeconds.value = exitDurSeconds
            preloadedBusLegRoadPoints.value = busLegRoadPoints
            preloadedRoadRoutePoints.value = emptyList()
        }

        fun seedPreloadedRoadRoute(points: List<GeoPoint>) {
            preloadedRoadRoutePoints.value = points
            activeMetroPlan.value = null
            preloadedBoardingRoadPoints.value = emptyList()
            preloadedExitRoadPoints.value = emptyList()
            preloadedBusLegRoadPoints.value = emptyMap()
        }

        fun enterMetroRideStage(plan: MetroJourneyPlan, legIndex: Int) {
            val leg = plan.legs.getOrNull(legIndex) ?: return
            currentMetroLegIndex.value = legIndex
            matchedMetroStationIndex = 0
            hasAlertFired.set(false)
        hasInterchangeAlertFiredForLeg.set(-1)
            metroStage.value = MetroJourneyStage.ON_METRO_LEG
            val legRoadPts = preloadedBusLegRoadPoints.value[legIndex]
            routePolyline.value = if (!legRoadPts.isNullOrEmpty()) legRoadPts else leg.trackPoints
            alternativeRoutes.value = emptyList()
            isRoadRouteActive.value = false
            metroStopsRemaining.value = leg.stopsCount
            metroCurrentStationName.value = leg.boardingStation.name
            metroNextStationName.value = leg.stations.getOrNull(1)?.name ?: leg.alightingStation.name
            val cfg = activeConfig.value
            val stopsBefore = if (legIndex < plan.legs.size - 1) {
                (cfg?.metroStopsBeforeAlert ?: 1).coerceIn(1, leg.stopsCount.coerceAtLeast(1))
            } else {
                (cfg?.metroStopsBeforeAlert ?: 1).coerceIn(0, leg.stopsCount)
            }
            metroAlertStationName.value = plan.getAlertStopForLeg(legIndex, stopsBefore)?.name
            initialDistanceMeters.value = null
        }

        fun enterFinalDestinationRoadStage() {
            metroStage.value = MetroJourneyStage.TO_FINAL_DESTINATION_ROAD
            hasAlertFired.set(false)
        hasInterchangeAlertFiredForLeg.set(-1)
            matchedPolylineSegmentIndex = 0
            lastRouteFetchTime = 0L
            routePolyline.value = emptyList()
            alternativeRoutes.value = emptyList()
            allCandidateRoutes.value = emptyList()
            liveDistanceMeters.value = null
            initialDistanceMeters.value = null
            isRoadRouteActive.value = false
            metroInterchangePrompt.value = null
        }

        fun confirmBoardedMetro() {
            val plan = activeMetroPlan.value ?: return
            enterMetroRideStage(plan, 0)
        }

        fun confirmLineSwitched(context: Context) {
            val plan = activeMetroPlan.value ?: return
            val nextIdx = currentMetroLegIndex.value + 1
            if (nextIdx < plan.legs.size) {
                WakeMeAlarmController.stopAlert(context)
                metroInterchangePrompt.value = null
                enterMetroRideStage(plan, nextIdx)
            }
        }

        fun confirmExitedMetro(context: Context) {
            WakeMeAlarmController.stopAlert(context)
            enterFinalDestinationRoadStage()
        }

        fun start(context: Context, config: DestinationConfig) {
            val intent = Intent(context, WakeMeTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_NAME, config.name)
                putExtra(EXTRA_LATITUDE, config.latitude)
                putExtra(EXTRA_LONGITUDE, config.longitude)
                putExtra(EXTRA_RADIUS, config.radiusMeters)
                putExtra(EXTRA_VEHICLE_MODE, config.vehicleMode.name)
                putExtra(EXTRA_MODE, config.alertMode.name)
                putExtra(EXTRA_ROUTING, config.audioRouting.name)
                putExtra(EXTRA_NOTE, config.reminderNote)
                putExtra(EXTRA_METRO_STOPS_BEFORE, config.metroStopsBeforeAlert)
                config.metroBoardingLat?.let { putExtra(EXTRA_METRO_BOARDING_LAT, it) }
                config.metroBoardingLon?.let { putExtra(EXTRA_METRO_BOARDING_LON, it) }
                config.metroBoardingName?.let { putExtra(EXTRA_METRO_BOARDING_NAME, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeMeTrackingService::class.java))
        }

        fun selectRoute(routeId: String) {
            val all = allCandidateRoutes.value
            val target = all.find { it.id == routeId } ?: return
            selectedRouteId.value = target.id
            matchedPolylineSegmentIndex = 0
            routePolyline.value = target.points
            alternativeRoutes.value = all.filter { it.id != target.id }
            liveDistanceMeters.value = target.distanceMeters

            val speedKmh = currentSpeedKmh.value ?: 0f
            val speedMps = speedKmh / 3.6f
            if (speedMps > 1.0f) {
                val etaSec = (target.distanceMeters / speedMps).toInt()
                estimatedEtaMinutes.value = (etaSec / 60).coerceAtLeast(1)
            }
        }

        fun selectAlternativeRoute(candidate: RouteCandidate) {
            selectRoute(candidate.id)
        }

        /**
         * Computes minimum cross-track distance from a GPS fix to the active polyline.
         */
        fun distanceToPolylineMeters(lat: Double, lon: Double, polyline: List<GeoPoint>): Double {
            if (polyline.isEmpty()) return Double.MAX_VALUE
            if (polyline.size == 1) {
                return haversineDistanceMeters(lat, lon, polyline[0].latitude, polyline[0].longitude)
            }
            var minDistance = Double.MAX_VALUE
            for (i in 0 until polyline.size - 1) {
                val p1 = polyline[i]
                val p2 = polyline[i + 1]
                val d = distanceToSegmentMeters(lat, lon, p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                if (d < minDistance) {
                    minDistance = d
                    if (minDistance < 15.0) break
                }
            }
            return minDistance
        }

        /**
         * Fast metric perpendicular distance from point (lat, lon) to segment (lat1, lon1)-(lat2, lon2).
         */
        fun distanceToSegmentMeters(
            lat: Double, lon: Double,
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val midLat = (lat1 + lat2) * 0.5
            val cosLat = kotlin.math.cos(Math.toRadians(midLat))
            val mPerDegLat = 111320.0
            val mPerDegLon = 111320.0 * cosLat

            val x = (lon - lon1) * mPerDegLon
            val y = (lat - lat1) * mPerDegLat
            val dx = (lon2 - lon1) * mPerDegLon
            val dy = (lat2 - lat1) * mPerDegLat

            val segLenSq = dx * dx + dy * dy
            if (segLenSq < 1e-6) {
                return kotlin.math.hypot(x, y)
            }

            val t = ((x * dx + y * dy) / segLenSq).coerceIn(0.0, 1.0)
            val projX = t * dx
            val projY = t * dy
            return kotlin.math.hypot(x - projX, y - projY)
        }

        /**
         * Delegates to [RoadRoutingRepository.haversineDistanceMeters] to avoid duplicate math.
         */
        fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
            RoadRoutingRepository.haversineDistanceMeters(lat1, lon1, lat2, lon2)
    }
}
