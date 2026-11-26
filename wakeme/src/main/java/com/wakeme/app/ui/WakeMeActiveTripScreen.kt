package com.wakeme.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wakeme.app.alarm.WakeMeAlarmController
import com.wakeme.app.service.MetroJourneyStage
import com.wakeme.app.service.WakeMeTrackingService
import com.wakeme.app.util.LocationMarkerUtils
import com.wakeme.app.data.MetroJourneyPlan
import com.wakeme.app.data.RouteCandidate
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.model.VehicleMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Screen 3: Active Commute Tracking Screen ("After start").
 * Faithful implementation of user's notebook sketch:
 * - Card 1 (Top): Destination name, giant live remaining distance, and Trip Progress bar.
 * - Card 2 (Middle): Moving navigation arrow map with [Expand] button to open full-screen map.
 * - Card 3 (Bottom): Sleep Guard Active ("Not sure" box) with [Test Ring 🔊] button.
 * - Bottom Bar: Dedicated prominent [ END COMMUTE / STOP ALARM ] button with confirmation dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeMeActiveTripScreen(
    viewModel: WakeMeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val targetName by viewModel.targetName.collectAsState()
    val targetLocation by viewModel.targetLocation.collectAsState()
    val radiusMeters by viewModel.radiusMeters.collectAsState()
    val selectedVehicleMode by viewModel.selectedVehicleMode.collectAsState()

    val liveDistance by WakeMeTrackingService.liveDistanceMeters.collectAsState()
    val straightDist by WakeMeTrackingService.straightLineDistanceMeters.collectAsState()
    val currentSpeed by WakeMeTrackingService.currentSpeedKmh.collectAsState()
    val estimatedEta by WakeMeTrackingService.estimatedEtaMinutes.collectAsState()
    val initialDist by WakeMeTrackingService.initialDistanceMeters.collectAsState()
    val isSnoozed by WakeMeAlarmController.isSnoozed.collectAsState()
    val routePoints by WakeMeTrackingService.routePolyline.collectAsState()
    val alternativeRoutes by WakeMeTrackingService.alternativeRoutes.collectAsState()
    val isRoadRoute by WakeMeTrackingService.isRoadRouteActive.collectAsState()
    val currentBearing by WakeMeTrackingService.currentBearing.collectAsState()

    val metroStage by WakeMeTrackingService.metroStage.collectAsState()
    val activeMetroPlan by WakeMeTrackingService.activeMetroPlan.collectAsState()
    val currentMetroLegIndex by WakeMeTrackingService.currentMetroLegIndex.collectAsState()
    val metroStopsRemaining by WakeMeTrackingService.metroStopsRemaining.collectAsState()
    val metroCurrentStationName by WakeMeTrackingService.metroCurrentStationName.collectAsState()
    val metroNextStationName by WakeMeTrackingService.metroNextStationName.collectAsState()
    val metroAlertStationName by WakeMeTrackingService.metroAlertStationName.collectAsState()
    val metroInterchangePrompt by WakeMeTrackingService.metroInterchangePrompt.collectAsState()
    val metroStopsBeforeAlert by viewModel.metroStopsBeforeAlert.collectAsState()
    val preloadedBoardingRoadPoints by WakeMeTrackingService.preloadedBoardingRoadPoints.collectAsState()
    val preloadedExitRoadPoints by WakeMeTrackingService.preloadedExitRoadPoints.collectAsState()
    val preloadedBoardingRoadDist by WakeMeTrackingService.preloadedBoardingRoadDistanceMeters.collectAsState()
    val preloadedBoardingRoadDur by WakeMeTrackingService.preloadedBoardingRoadDurationSeconds.collectAsState()
    val preloadedExitRoadDist by WakeMeTrackingService.preloadedExitRoadDistanceMeters.collectAsState()
    val preloadedExitRoadDur by WakeMeTrackingService.preloadedExitRoadDurationSeconds.collectAsState()
    val preloadedBusLegRoadPoints by WakeMeTrackingService.preloadedBusLegRoadPoints.collectAsState()
    val currentLiveLoc by WakeMeTrackingService.currentLocationPoint.collectAsState()
    val currentOriginLoc by viewModel.currentOriginLocation.collectAsState()

    var isTestingAlert by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }
    var isHeadingUp by remember { mutableStateOf(true) }
    var showEndCommuteConfirmation by remember { mutableStateOf(false) }
    var inlineMapViewRef by remember { mutableStateOf<MapView?>(null) }
    var inlineLocOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, inlineMapViewRef) {
        val map = inlineMapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    map?.onResume()
                    inlineLocOverlayRef?.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    inlineLocOverlayRef?.disableMyLocation()
                    map?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inlineLocOverlayRef?.disableMyLocation()
            map?.onPause()
            map?.onDetach()
        }
    }

    // Calculate journey progress percentage (0.0 to 1.0)
    val progress = remember(liveDistance, initialDist) {
        val dist = liveDistance
        val init = initialDist
        if (dist != null && init != null && init > 0f) {
            val traveled = (init - dist).coerceAtLeast(0f)
            (traveled / init).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Commute Tracking",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    val badgeText = if (selectedVehicleMode == VehicleMode.METRO || selectedVehicleMode == VehicleMode.BUS) {
                        if (metroStage == MetroJourneyStage.TO_FINAL_DESTINATION_ROAD) {
                            "Stage 3 • 150m Road"
                        } else if (metroStopsBeforeAlert == 0) {
                            "Alert • 50m"
                        } else {
                            "Alert • $metroStopsBeforeAlert stop${if (metroStopsBeforeAlert > 1) "s" else ""}"
                        }
                    } else {
                        val radiusLabel = if (radiusMeters >= 1000f) "${(radiusMeters / 1000f).toInt()}km" else "${radiusMeters.toInt()}m"
                        val prefix = if (selectedVehicleMode.usesRoadDistanceTrigger) "Road Alert" else "Radius"
                        "$prefix: $radiusLabel"
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Dedicated End Button (from sketch: "End button is needed")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            showEndCommuteConfirmation = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "End Commute",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Snooze Banner (if user snoozed the active alarm)
            if (isSnoozed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Alarm Snoozed (Resuming in ~2 mins)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 1. TOP CARD: Destination, Distance & Trip Progress
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isBusJourney = selectedVehicleMode == VehicleMode.BUS || activeMetroPlan?.isBusJourney == true
                    val isMetroJourney = (selectedVehicleMode == VehicleMode.METRO || selectedVehicleMode == VehicleMode.BUS) && activeMetroPlan != null

                    // Destination Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (selectedVehicleMode) {
                                VehicleMode.BUS -> Icons.Default.DirectionsBus
                                VehicleMode.METRO -> Icons.Default.DirectionsSubway
                                else -> Icons.Default.DirectionsCar
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = targetName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isMetroJourney) {
                        LiveStageStepperPill(
                            stage = metroStage,
                            isBus = isBusJourney,
                            hasStage3 = (activeMetroPlan?.exitWalkDistanceMeters ?: 0f) > 50f
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (isMetroJourney && (metroStage == MetroJourneyStage.ON_METRO_LEG || metroStage == MetroJourneyStage.INTERCHANGE_WAIT)) {
                        val activeLeg = activeMetroPlan!!.legs.getOrNull(currentMetroLegIndex)
                        if (activeLeg != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(activeLeg.lineColorInt).copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (activeLeg.isBusLeg) {
                                        "Bus ${activeLeg.lineName} • ${activeLeg.directionLabel}"
                                    } else {
                                        "${activeLeg.lineName} Line • ${activeLeg.directionLabel}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(activeLeg.lineColorInt),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // Live Distance / Stops Countdown
                    if (isMetroJourney && (metroStage == MetroJourneyStage.ON_METRO_LEG || metroStage == MetroJourneyStage.INTERCHANGE_WAIT)) {
                        val stopsLeft = metroStopsRemaining ?: 0
                        val stopsLabel = when (stopsLeft) {
                            0 -> "Arriving!"
                            1 -> "1 stop"
                            else -> "$stopsLeft stops"
                        }
                        Text(
                            text = stopsLabel,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        val activeLeg = activeMetroPlan?.legs?.getOrNull(currentMetroLegIndex)
                        if (activeLeg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LiveUpcomingStopsTimeline(
                                leg = activeLeg,
                                currentStationName = metroCurrentStationName,
                                nextStationName = metroNextStationName,
                                alertStationName = metroAlertStationName
                            )
                        } else {
                            val nextName = metroNextStationName ?: activeMetroPlan?.exitStation?.name ?: targetName
                            val alertAt = metroAlertStationName ?: activeMetroPlan?.exitStation?.name ?: targetName
                            Text(
                                text = "NEXT: $nextName • ALERT AT: $alertAt",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val isOrangeRoadStage = isMetroJourney &&
                            (metroStage == MetroJourneyStage.TO_BOARDING_ROAD || metroStage == MetroJourneyStage.TO_FINAL_DESTINATION_ROAD)
                        val fallbackDist = if (metroStage == MetroJourneyStage.TO_BOARDING_ROAD) preloadedBoardingRoadDist else if (metroStage == MetroJourneyStage.TO_FINAL_DESTINATION_ROAD) preloadedExitRoadDist else null
                        val dist = liveDistance ?: fallbackDist ?: (straightDist?.let { it * 1.28f })
                        val distanceText = if (dist != null) {
                            if (dist >= 1000f) {
                                String.format("%.1f km", dist / 1000f)
                            } else {
                                "${dist.toInt()} m"
                            }
                        } else {
                            "Calculating..."
                        }

                        Text(
                            text = distanceText,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isOrangeRoadStage) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        val boardStopTitle = activeMetroPlan?.boardingStation?.name ?: if (isBusJourney) "Bus Stop" else "Metro Station"
                        Text(
                            text = if (isMetroJourney && metroStage == MetroJourneyStage.TO_BOARDING_ROAD) {
                                "To $boardStopTitle • Switches to ride at 50m"
                            } else if (isMetroJourney && metroStage == MetroJourneyStage.TO_FINAL_DESTINATION_ROAD) {
                                "Final road leg to destination • Alerts at 150m"
                            } else if (isRoadRoute) {
                                "Road distance remaining"
                            } else {
                                "Estimated distance remaining"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isOrangeRoadStage) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Road Travel Time Preview in Orange for upcoming Stage 3 exit leg
                    if (isMetroJourney && metroStage != MetroJourneyStage.TO_FINAL_DESTINATION_ROAD) {
                        val eDist = preloadedExitRoadDist
                        val eDur = preloadedExitRoadDur
                        if (eDist != null && (activeMetroPlan?.exitWalkDistanceMeters ?: 0f) > 50f) {
                            val ed = if (eDist >= 1000f) String.format("%.1f km", eDist / 1000f) else "${eDist.toInt()}m"
                            val em = ((eDur ?: (eDist / 8.33f)) / 60).toInt().coerceAtLeast(1)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "After exit (${activeMetroPlan?.exitStation?.name}): $ed (~$em min) by road",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Stage Quick Controls & Interchange Prompts
                    if (isMetroJourney && metroStage == MetroJourneyStage.TO_BOARDING_ROAD) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = { WakeMeTrackingService.confirmBoardedMetro() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isBusJourney) "Boarded Bus →" else "Boarded Metro →",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    } else if (isMetroJourney && (metroStage == MetroJourneyStage.INTERCHANGE_WAIT || metroInterchangePrompt != null)) {
                        val nextLeg = activeMetroPlan?.legs?.getOrNull(currentMetroLegIndex + 1)
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = metroInterchangePrompt ?: "Transfer Ahead",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    textAlign = TextAlign.Center
                                )
                                if (nextLeg != null) {
                                    Button(
                                        onClick = { WakeMeTrackingService.confirmLineSwitched(context) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(nextLeg.lineColorInt))
                                    ) {
                                        Text(
                                            if (nextLeg.isBusLeg) "Boarded Bus ${nextLeg.lineName} →" else "Boarded ${nextLeg.lineName} Line →",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    } else if (isMetroJourney &&
                        metroStage == MetroJourneyStage.ON_METRO_LEG &&
                        currentMetroLegIndex == (activeMetroPlan?.legs?.size ?: 1) - 1 &&
                        (activeMetroPlan?.exitWalkDistanceMeters ?: 0f) > 50f
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = { WakeMeTrackingService.confirmExitedMetro(context) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Exited ${activeMetroPlan?.exitStation?.name ?: if (isBusJourney) "Bus" else "Metro"} • Route to Dest →",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Trip Progress Bar (0% to 100%)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Trip Progress",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Live Telemetry Row (Speed & ETA)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${(currentSpeed ?: 0f).toInt()} km/h",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = estimatedEta?.let { "~$it min ETA" } ?: "Approaching",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 2. MIDDLE CARD: Map with [Expand] Button (from sketch: Map with [Expand])
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(16.0)
                                val startCenter = currentLiveLoc
                                    ?: currentOriginLoc
                                    ?: activeMetroPlan?.boardingStation?.geoPoint
                                    ?: targetLocation
                                controller.setCenter(startCenter)

                                val myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                                    enableMyLocation()
                                    enableFollowLocation()
                                }
                                inlineLocOverlayRef = myLocOverlay
                                LocationMarkerUtils.applyModernLocationOverlay(ctx, myLocOverlay)
                                overlays.add(myLocOverlay)
                                inlineMapViewRef = this

                                val isBus = selectedVehicleMode == VehicleMode.BUS
                                val isMetro = selectedVehicleMode == VehicleMode.METRO
                                val busCat = BmtcBusCategory.fromRouteOrName(targetName)

                                val destMarker = Marker(this).apply {
                                    position = targetLocation
                                    title = targetName
                                    icon = when {
                                        isBus -> LocationMarkerUtils.createTwoToneBusBadgeDrawable(ctx, busCat, sizeDp = 30)
                                        isMetro -> LocationMarkerUtils.createStationMarkerDrawable(ctx, targetName, NammaMetroNetwork.COLOR_PURPLE)
                                        else -> LocationMarkerUtils.createModernPinDrawable(ctx)
                                    }
                                    setAnchor(Marker.ANCHOR_CENTER, if (isBus) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM)
                                }
                                overlays.add(destMarker)
                            }
                        },
                        update = { map ->
                            applyActiveTripMapOverlays(
                                map = map,
                                selectedVehicleMode = selectedVehicleMode,
                                targetLocation = targetLocation,
                                targetName = targetName,
                                radiusMeters = radiusMeters,
                                routePoints = routePoints,
                                alternativeRoutes = alternativeRoutes,
                                activeMetroPlan = activeMetroPlan,
                                metroStage = metroStage,
                                currentMetroLegIndex = currentMetroLegIndex,
                                metroStopsBeforeAlert = metroStopsBeforeAlert,
                                preloadedBoardingRoadPoints = preloadedBoardingRoadPoints,
                                preloadedExitRoadPoints = preloadedExitRoadPoints,
                                busLegRoadPoints = preloadedBusLegRoadPoints,
                                isHeadingUp = isHeadingUp,
                                currentBearing = currentBearing
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Expand Button in Top-Right Corner (from sketch: [Expand])
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isMapExpanded = true },
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Expand Map",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Expand",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Compass Orientation Toggle in Bottom-Right
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isHeadingUp = !isHeadingUp },
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isHeadingUp) Icons.Default.Explore else Icons.Default.North,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isHeadingUp) "Heading" else "North",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Sleep Guard Status & Test Ring
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sleep Guard Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Alarm sounds at full volume even on Silent or DND.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (!isTestingAlert) {
                                isTestingAlert = true
                                val config = viewModel.getDestinationConfig()
                                WakeMeAlarmController.triggerAlert(context, config)
                                coroutineScope.launch {
                                    delay(5000L)
                                    WakeMeAlarmController.stopAlert(context)
                                    isTestingAlert = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isTestingAlert) Icons.Default.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTestingAlert) "Sounding Alert Test (5s)..." else "Test Alarm Sound",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // End Commute Confirmation Dialog
    if (showEndCommuteConfirmation) {
        AlertDialog(
            onDismissRequest = { showEndCommuteConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "End Commute?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to stop GPS tracking and cancel your destination alarm?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndCommuteConfirmation = false
                        viewModel.stopTracking(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("End Commute")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndCommuteConfirmation = false }) {
                    Text("Resume Trip")
                }
            }
        )
    }

    // Full-Screen Interactive Map Modal Dialog (when Expand is tapped)
    if (isMapExpanded) {
        Dialog(
            onDismissRequest = { isMapExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var expandedMapRef by remember { mutableStateOf<MapView?>(null) }
            var expandedLocRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

            DisposableEffect(expandedMapRef) {
                val map = expandedMapRef
                map?.onResume()
                onDispose {
                    expandedLocRef?.disableMyLocation()
                    map?.onPause()
                    map?.onDetach()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(16.0)
                            val startCenter = currentLiveLoc
                                ?: currentOriginLoc
                                ?: activeMetroPlan?.boardingStation?.geoPoint
                                ?: targetLocation
                            controller.setCenter(startCenter)

                            val myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                                enableMyLocation()
                                enableFollowLocation()
                            }
                            expandedLocRef = myLocOverlay
                            LocationMarkerUtils.applyModernLocationOverlay(ctx, myLocOverlay)
                            overlays.add(myLocOverlay)

                            val destMarker = Marker(this).apply {
                                position = targetLocation
                                title = targetName
                                icon = LocationMarkerUtils.createModernPinDrawable(ctx)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            overlays.add(destMarker)
                            expandedMapRef = this
                        }
                    },
                    update = { map ->
                        applyActiveTripMapOverlays(
                            map = map,
                            selectedVehicleMode = selectedVehicleMode,
                            targetLocation = targetLocation,
                            targetName = targetName,
                            radiusMeters = radiusMeters,
                            routePoints = routePoints,
                            alternativeRoutes = alternativeRoutes,
                            activeMetroPlan = activeMetroPlan,
                            metroStage = metroStage,
                            currentMetroLegIndex = currentMetroLegIndex,
                            metroStopsBeforeAlert = metroStopsBeforeAlert,
                            preloadedBoardingRoadPoints = preloadedBoardingRoadPoints,
                            preloadedExitRoadPoints = preloadedExitRoadPoints,
                            busLegRoadPoints = preloadedBusLegRoadPoints,
                            isHeadingUp = false,
                            currentBearing = null
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Close Button on Expanded Map
                FloatingActionButton(
                    onClick = { isMapExpanded = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Map")
                }
            }
        }
    }
}

private fun applyActiveTripMapOverlays(
    map: MapView,
    selectedVehicleMode: VehicleMode,
    targetLocation: GeoPoint,
    targetName: String,
    radiusMeters: Float,
    routePoints: List<GeoPoint>,
    alternativeRoutes: List<RouteCandidate>,
    activeMetroPlan: MetroJourneyPlan?,
    metroStage: MetroJourneyStage,
    currentMetroLegIndex: Int,
    metroStopsBeforeAlert: Int,
    preloadedBoardingRoadPoints: List<GeoPoint>,
    preloadedExitRoadPoints: List<GeoPoint>,
    busLegRoadPoints: Map<Int, List<GeoPoint>>,
    isHeadingUp: Boolean,
    currentBearing: Float?
) {
    val desiredOrientation = if (isHeadingUp && currentBearing != null) -currentBearing else 0f
    val orientationChanged = kotlin.math.abs(map.mapOrientation - desiredOrientation) > 1f
    if (orientationChanged) {
        map.mapOrientation = desiredOrientation
    }

    val renderKey = listOf(
        selectedVehicleMode.name,
        metroStage.name,
        currentMetroLegIndex,
        metroStopsBeforeAlert,
        routePoints.size,
        routePoints.firstOrNull()?.latitude,
        alternativeRoutes.size,
        activeMetroPlan?.totalStops,
        preloadedBoardingRoadPoints.size,
        preloadedExitRoadPoints.size,
        busLegRoadPoints.values.sumOf { it.size },
        radiusMeters.toInt()
    ).joinToString("|")

    if (map.tag == renderKey) {
        if (orientationChanged) map.invalidate()
        return
    }
    map.tag = renderKey

    val isBus = selectedVehicleMode == VehicleMode.BUS
    val isMetro = selectedVehicleMode == VehicleMode.METRO
    val isTransit = isBus || isMetro
    val busCat = BmtcBusCategory.fromRouteOrName(targetName)
    val themeColorInt = when {
        isBus -> busCat.primaryColorInt
        isMetro -> NammaMetroNetwork.COLOR_PURPLE
        else -> 0xFF1E88E5.toInt()
    }

    if (isTransit && activeMetroPlan != null) {
        if (metroStage == MetroJourneyStage.TO_FINAL_DESTINATION_ROAD) {
            LocationMarkerUtils.updateRoadAndAlertOverlays(
                map = map,
                vehicleMode = VehicleMode.CAR,
                targetLocation = targetLocation,
                alertDistanceMeters = 150f,
                primaryRoutePoints = if (routePoints.size >= 2) routePoints else preloadedExitRoadPoints,
                alternativeRoutes = alternativeRoutes,
                themeColorInt = LocationMarkerUtils.COLOR_METRO_ROAD_ORANGE,
                targetName = targetName,
                primaryColorOverride = LocationMarkerUtils.COLOR_METRO_ROAD_ORANGE
            )
        } else {
            val boardPts = when (metroStage) {
                MetroJourneyStage.TO_BOARDING_ROAD, MetroJourneyStage.NONE ->
                    if (routePoints.size >= 2) routePoints else preloadedBoardingRoadPoints
                else -> emptyList()
            }
            val zoomPts = LocationMarkerUtils.renderMetroJourneyOverlays(
                map = map,
                context = map.context,
                plan = activeMetroPlan,
                boardingRoadPoints = boardPts,
                stopsBeforeAlert = metroStopsBeforeAlert,
                activeLegIndex = if (metroStage == MetroJourneyStage.TO_BOARDING_ROAD) 0 else currentMetroLegIndex,
                exitRoadPoints = preloadedExitRoadPoints,
                busLegRoadPoints = if (activeMetroPlan.isBusJourney) busLegRoadPoints else emptyMap()
            )
            if (zoomPts.size >= 2) {
                map.post {
                    try {
                        map.zoomToBoundingBox(BoundingBox.fromGeoPoints(zoomPts), true, 48)
                    } catch (_: Exception) {}
                }
            }
        }
    } else {
        LocationMarkerUtils.updateRoadAndAlertOverlays(
            map = map,
            vehicleMode = selectedVehicleMode,
            targetLocation = targetLocation,
            alertDistanceMeters = radiusMeters,
            primaryRoutePoints = routePoints,
            alternativeRoutes = alternativeRoutes,
            themeColorInt = themeColorInt,
            targetName = targetName
        )
    }

    map.invalidate()
}
