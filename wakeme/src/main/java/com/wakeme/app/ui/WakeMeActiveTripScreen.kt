package com.wakeme.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wakeme.app.alarm.WakeMeAlarmController
import com.wakeme.app.data.MetroJourneyPlan
import com.wakeme.app.data.RouteCandidate
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.model.VehicleMode
import com.wakeme.app.service.MetroJourneyStage
import com.wakeme.app.service.WakeMeTrackingService
import com.wakeme.app.ui.theme.LocalWakeMeIsDark
import com.wakeme.app.ui.theme.LocalWakeMePalette
import com.wakeme.app.ui.theme.WakeMeMotion
import com.wakeme.app.util.LocationMarkerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

@Composable
fun WakeMeActiveTripScreen(
    viewModel: WakeMeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val p = LocalWakeMePalette.current
    val isDark = LocalWakeMeIsDark.current

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
    val currentBearing by WakeMeTrackingService.currentBearing.collectAsState()

    val metroStage by WakeMeTrackingService.metroStage.collectAsState()
    val activeMetroPlan by WakeMeTrackingService.activeMetroPlan.collectAsState()
    val currentMetroLegIndex by WakeMeTrackingService.currentMetroLegIndex.collectAsState()
    val metroStopsRemaining by WakeMeTrackingService.metroStopsRemaining.collectAsState()
    val metroCurrentStationName by WakeMeTrackingService.metroCurrentStationName.collectAsState()
    val metroAlertStationName by WakeMeTrackingService.metroAlertStationName.collectAsState()
    val metroStopsBeforeAlert by viewModel.metroStopsBeforeAlert.collectAsState()
    val preloadedBoardingRoadPoints by WakeMeTrackingService.preloadedBoardingRoadPoints.collectAsState()
    val preloadedExitRoadPoints by WakeMeTrackingService.preloadedExitRoadPoints.collectAsState()
    val preloadedBoardingRoadDist by WakeMeTrackingService.preloadedBoardingRoadDistanceMeters.collectAsState()
    val preloadedExitRoadDist by WakeMeTrackingService.preloadedExitRoadDistanceMeters.collectAsState()
    val preloadedBusLegRoadPoints by WakeMeTrackingService.preloadedBusLegRoadPoints.collectAsState()
    val currentLiveLoc by WakeMeTrackingService.currentLocationPoint.collectAsState()
    val currentOriginLoc by viewModel.currentOriginLocation.collectAsState()

    var isTestingAlert by remember { mutableStateOf(false) }
    var showEndCommuteConfirmation by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val map = mapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    map?.onResume()
                    locOverlayRef?.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    locOverlayRef?.disableMyLocation()
                    map?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map?.onPause()
            map?.onDetach()
        }
    }

    val isMetroJourney = activeMetroPlan != null &&
        (selectedVehicleMode == VehicleMode.METRO || selectedVehicleMode == VehicleMode.BUS)
    val isBusJourney = activeMetroPlan?.isBusJourney == true || selectedVehicleMode == VehicleMode.BUS

    val activeStageIdx = when {
        !isMetroJourney -> 0
        metroStage == MetroJourneyStage.ON_METRO_LEG || metroStage == MetroJourneyStage.INTERCHANGE_WAIT -> 1
        metroStage == MetroJourneyStage.TO_FINAL_DESTINATION_ROAD -> 2
        else -> 0
    }

    val distMeters = liveDistance
        ?: (if (activeStageIdx == 0) preloadedBoardingRoadDist else if (activeStageIdx == 2) preloadedExitRoadDist else null)
        ?: straightDist
    val distLabel = distMeters?.let {
        if (it >= 1000f) String.format(Locale.US, "%.1f km", it / 1000f) else "${it.toInt()} m"
    } ?: "Locating..."

    val roadAlertLabel = if (radiusMeters >= 1000f) "${(radiusMeters / 1000f).toInt()} km" else "${radiusMeters.toInt()}m"

    val rawProgress = when {
        isMetroJourney && activeStageIdx == 1 -> {
            val legTotal = activeMetroPlan?.legs?.getOrNull(currentMetroLegIndex)?.stopsCount?.coerceAtLeast(1) ?: 1
            val rem = (metroStopsRemaining ?: legTotal).coerceIn(0, legTotal)
            1f - (rem.toFloat() / legTotal.toFloat())
        }
        initialDist != null && distMeters != null && initialDist!! > 10f ->
            (1f - (distMeters / initialDist!!)).coerceIn(0.05f, 1f)
        else -> 0.25f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0.04f, 1f),
        animationSpec = WakeMeMotion.elasticFloatSpec(),
        label = "activeProgress"
    )

    Box(modifier = modifier.fillMaxSize().background(p.bg)) {
        // 1. Full-Screen Active Trip MapView (with Google Maps Dark Filter & Silver 3D Vehicle Marker)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(15.5)
                    val startCenter = currentLiveLoc
                        ?: currentOriginLoc
                        ?: activeMetroPlan?.boardingStation?.geoPoint
                        ?: targetLocation
                    controller.setCenter(startCenter)

                    val myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation()
                        enableFollowLocation()
                    }
                    locOverlayRef = myLocOverlay
                    LocationMarkerUtils.applyModernLocationOverlay(ctx, myLocOverlay)
                    overlays.add(myLocOverlay)
                    mapViewRef = this
                }
            },
            update = { map ->
                applyActiveTripMapOverlays(
                    map = map,
                    isDark = isDark,
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
                    currentBearing = currentBearing,
                    currentLiveLoc = currentLiveLoc
                )
            }
        )

        // 2. Top Safe-Area Direction Cue Banner + Test Ring / Snooze Quick Action
        val (cueArrow, cueMain, cueSub) = activeDirectionCue(
            isMetroJourney = isMetroJourney,
            isBusJourney = isBusJourney,
            activeStageIdx = activeStageIdx,
            plan = activeMetroPlan,
            targetName = targetName,
            distLabel = distLabel,
            roadAlertLabel = roadAlertLabel,
            alertStationName = metroAlertStationName
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = p.cueBg,
            border = BorderStroke(1.dp, p.hairline),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.orange),
                    contentAlignment = Alignment.Center
                ) {
                    Text(cueArrow, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(cueMain, color = p.cueInk, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cueSub, color = p.cueInk.copy(alpha = 0.78f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(
                    onClick = {
                        if (isSnoozed) {
                            WakeMeAlarmController.stopAlert(context)
                        } else if (!isTestingAlert) {
                            isTestingAlert = true
                            coroutineScope.launch {
                                WakeMeAlarmController.triggerAlert(context, viewModel.getDestinationConfig())
                                delay(2200)
                                WakeMeAlarmController.stopAlert(context)
                                isTestingAlert = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSnoozed) p.red else p.orange
                ) {
                    Text(
                        text = if (isSnoozed) "Stop Ring" else if (isTestingAlert) "Ringing..." else "Test",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 3. Persistent Active Trip Bottom Sheet (ui_preview layout)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = p.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(p.muted.copy(alpha = 0.30f))
                )
                Spacer(Modifier.height(10.dp))

                // Stage Pills for Metro / Bus
                if (isMetroJourney && activeMetroPlan != null) {
                    val stageLabels = listOf(
                        if (isBusJourney) "1. To Stop" else "1. To Metro",
                        if (isBusJourney) "2. On Bus" else "2. Metro (${activeMetroPlan!!.totalStops})",
                        "3. Final Road"
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        stageLabels.forEachIndexed { idx, label ->
                            val selected = idx == activeStageIdx
                            val isOrange = idx != 1
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    selected && isOrange -> p.orange
                                    selected -> p.ctaBg
                                    else -> p.bg
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = when {
                                        selected && isOrange -> Color.White
                                        selected -> p.ctaInk
                                        else -> p.muted
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val accentColor = when {
                    activeStageIdx == 1 && isBusJourney -> p.blue
                    activeStageIdx == 1 && isMetroJourney -> p.purple
                    else -> p.orange
                }
                val stageBadge = when {
                    !isMetroJourney -> "DIRECT ROAD • LIVE TRACKING"
                    activeStageIdx == 0 -> "STAGE 1 • TO BOARDING"
                    activeStageIdx == 1 -> if (isBusJourney) "STAGE 2 • ON BMTC BUS" else "STAGE 2 • ON NAMMA METRO"
                    else -> "STAGE 3 • FINAL APPROACH"
                }
                Text(stageBadge, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                val heroText = if (isMetroJourney && activeStageIdx == 1) {
                    val rem = metroStopsRemaining ?: 0
                    if (rem == 0) "Arriving!" else "$rem left"
                } else distLabel

                val speedEtaText = buildString {
                    val spd = currentSpeed
                    if (spd != null && spd > 1f) append("${spd.toInt()} km/h")
                    if (estimatedEta != null && estimatedEta!! > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("~${estimatedEta}m")
                    }
                    if (isEmpty()) append("GPS Active")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = heroText,
                        color = p.ink,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = metroCurrentStationName ?: speedEtaText,
                        color = p.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(p.bg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accentColor)
                    )
                }

                // Scrollable Stop List with Moving M/B Live Stamp during Stage 2
                val activeLeg = activeMetroPlan?.legs?.getOrNull(currentMetroLegIndex)
                if (isMetroJourney && activeStageIdx == 1 && activeLeg != null) {
                    Spacer(Modifier.height(8.dp))
                    val stamp = if (isBusJourney) "B" else "M"
                    val alertStop = activeMetroPlan?.getAlertStopForLeg(currentMetroLegIndex, metroStopsBeforeAlert)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 152.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.bg)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeLeg.stations.forEachIndexed { idx, st ->
                            val isCurrent = metroCurrentStationName?.equals(st.name, ignoreCase = true) == true
                            val isAlert = (metroAlertStationName?.equals(st.name, ignoreCase = true) == true) ||
                                (alertStop?.id == st.id)
                            val dotCol = when {
                                isAlert -> p.red
                                activeLeg.isBusLeg -> p.blue
                                else -> Color(activeLeg.lineColorInt)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) dotCol.copy(alpha = 0.14f) else Color.Transparent)
                                    .padding(horizontal = if (isCurrent) 6.dp else 0.dp, vertical = if (isCurrent) 3.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier.size(18.dp).clip(CircleShape).background(dotCol),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stamp, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.size(if (idx == 0 || idx == activeLeg.stations.lastIndex || isAlert) 8.dp else 5.5.dp).clip(CircleShape).background(dotCol)
                                    )
                                }
                                Text(
                                    text = if (isAlert) "${st.name} (Alert)" else st.name,
                                    color = if (isCurrent || isAlert) p.ink else p.muted,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isCurrent || isAlert) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Text("LIVE", color = dotCol, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(accentColor))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    activeStageIdx == 0 && activeMetroPlan != null -> activeMetroPlan!!.boardingStation.name
                                    else -> targetName
                                },
                                color = p.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isMetroJourney && activeStageIdx == 0) "Auto-switches to transit within 50m" else "Reminder armed at $roadAlertLabel",
                                color = p.muted,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showEndCommuteConfirmation = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.red, contentColor = Color.White)
                ) {
                    Text("End Commute", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showEndCommuteConfirmation) {
        AlertDialog(
            onDismissRequest = { showEndCommuteConfirmation = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = p.red) },
            title = { Text("End Commute?", fontWeight = FontWeight.Bold) },
            text = { Text("Stop live GPS tracking and disarm the wake-up alarm?") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndCommuteConfirmation = false
                        WakeMeAlarmController.stopAlert(context)
                        viewModel.stopTracking(context)
                        viewModel.navigateTo(WakeMeScreen.SEARCH_LANDING)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = p.red)
                ) {
                    Text("End Commute", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndCommuteConfirmation = false }) { Text("Keep Tracking") }
            }
        )
    }
}

private fun applyActiveTripMapOverlays(
    map: MapView,
    isDark: Boolean,
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
    currentBearing: Float?,
    currentLiveLoc: GeoPoint?
) {
    LocationMarkerUtils.applyDarkMapFilter(map, isDark)

    // Update or add silver 3D-styled vehicle marker at live GPS position
    val showSilverVehicle = currentLiveLoc != null && (
        selectedVehicleMode.usesRoadDistanceTrigger || metroStage == MetroJourneyStage.ON_METRO_LEG
    )
    val existingVeh = map.overlays.filterIsInstance<Marker>().firstOrNull { it.title == "WakeMeLiveVehicle" }
    if (showSilverVehicle && currentLiveLoc != null) {
        val vehIcon = LocationMarkerUtils.createSilverVehicleDrawable(map.context, selectedVehicleMode, currentBearing ?: 0f)
        if (existingVeh != null) {
            existingVeh.position = currentLiveLoc
            existingVeh.icon = vehIcon
        } else {
            map.overlays.add(Marker(map).apply {
                position = currentLiveLoc
                title = "WakeMeLiveVehicle"
                icon = vehIcon
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
        }
    } else if (existingVeh != null) {
        map.overlays.remove(existingVeh)
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
        radiusMeters.toInt(),
        isDark
    ).joinToString("|")

    if (map.tag == renderKey) {
        map.invalidate()
        return
    }
    map.tag = renderKey

    val isBus = selectedVehicleMode == VehicleMode.BUS
    val isMetro = selectedVehicleMode == VehicleMode.METRO
    val busCat = BmtcBusCategory.fromRouteOrName(targetName)
    val themeColorInt = when {
        isBus -> busCat.primaryColorInt
        isMetro -> NammaMetroNetwork.COLOR_PURPLE
        else -> 0xFF1E88E5.toInt()
    }

    val zoomPts = mutableListOf<GeoPoint>()
    if ((isMetro || isBus) && activeMetroPlan != null) {
        zoomPts.addAll(
            LocationMarkerUtils.renderMetroJourneyOverlays(
                map = map,
                context = map.context,
                plan = activeMetroPlan,
                boardingRoadPoints = preloadedBoardingRoadPoints,
                stopsBeforeAlert = metroStopsBeforeAlert,
                exitRoadPoints = preloadedExitRoadPoints,
                activeLegIndex = if (metroStage == MetroJourneyStage.ON_METRO_LEG) currentMetroLegIndex else -1,
                busLegRoadPoints = if (activeMetroPlan.isBusJourney) busLegRoadPoints else emptyMap()
            )
        )
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
        zoomPts.addAll(routePoints)
    }

    if (zoomPts.size >= 2) {
        map.post {
            runCatching {
                val box = BoundingBox.fromGeoPoints(zoomPts)
                val latSpan = (box.latNorth - box.latSouth).coerceAtLeast(0.004)
                val lonSpan = (box.lonEast - box.lonWest).coerceAtLeast(0.004)
                val padded = BoundingBox(
                    box.latNorth + latSpan * 0.25,
                    box.lonEast + lonSpan * 0.18,
                    box.latSouth - latSpan * 1.15,
                    box.lonWest - lonSpan * 0.18
                )
                map.zoomToBoundingBox(padded, true, 56)
            }
        }
    }
    map.invalidate()
}

private fun activeDirectionCue(
    isMetroJourney: Boolean,
    isBusJourney: Boolean,
    activeStageIdx: Int,
    plan: MetroJourneyPlan?,
    targetName: String,
    distLabel: String,
    roadAlertLabel: String,
    alertStationName: String?
): Triple<String, String, String> {
    if (!isMetroJourney || plan == null) {
        return Triple("↑", "$distLabel to $targetName", "Alert armed at $roadAlertLabel")
    }
    return when (activeStageIdx) {
        0 -> Triple("↗", "$distLabel to ${plan.boardingStation.name}", "Switches to transit tracking at 50m")
        1 -> Triple(if (isBusJourney) "B" else "M", plan.legs.joinToString(" ➔ ") { it.lineName }, "Alert at ${alertStationName ?: plan.exitStation.name}")
        else -> Triple("↑", "$distLabel to $targetName", "Final approach • Alert at 150m")
    }
}
