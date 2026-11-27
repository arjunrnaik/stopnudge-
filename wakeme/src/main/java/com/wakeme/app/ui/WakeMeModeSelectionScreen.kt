package com.wakeme.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.model.VehicleMode
import com.wakeme.app.ui.theme.LocalWakeMeIsDark
import com.wakeme.app.ui.theme.LocalWakeMePalette
import com.wakeme.app.ui.theme.WakeMeMotion
import com.wakeme.app.util.LocationMarkerUtils
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WakeMeModeSelectionScreen(
    viewModel: WakeMeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val p = LocalWakeMePalette.current
    val isDark = LocalWakeMeIsDark.current

    val targetName by viewModel.targetName.collectAsState()
    val targetLocation by viewModel.targetLocation.collectAsState()
    val selectedVehicleMode by viewModel.selectedVehicleMode.collectAsState()
    val radiusMeters by viewModel.radiusMeters.collectAsState()
    val audioRouting by viewModel.audioRouting.collectAsState()
    val vibrateEnabled by viewModel.vibrateEnabled.collectAsState()
    val selectedBusCategory by viewModel.selectedBusCategory.collectAsState()
    val previewRoadRoute by viewModel.previewRoadRoute.collectAsState()
    val isLoadingPreviewRoute by viewModel.isLoadingPreviewRoute.collectAsState()
    val metroStopsBeforeAlert by viewModel.metroStopsBeforeAlert.collectAsState()
    val customBoardingStation by viewModel.customBoardingStation.collectAsState()
    val customBoardingBusStop by viewModel.customBoardingBusStop.collectAsState()
    val metroJourneyPlan by viewModel.metroJourneyPlan.collectAsState()
    val bmtcRouteOptions by viewModel.bmtcRouteOptions.collectAsState()
    val selectedBusRouteIndex by viewModel.selectedBusRouteIndex.collectAsState()
    val busLegRoadPoints by viewModel.busLegRoadPoints.collectAsState()
    val boardingRoadRoute by viewModel.boardingRoadRoute.collectAsState()
    val exitRoadRoute by viewModel.exitRoadRoute.collectAsState()
    val currentOriginLoc by viewModel.currentOriginLocation.collectAsState()

    val isRoadMode = selectedVehicleMode.usesRoadDistanceTrigger
    val isMetroMode = selectedVehicleMode == VehicleMode.METRO
    val isBusMode = selectedVehicleMode == VehicleMode.BUS
    val isTransitMode = isMetroMode || isBusMode

    var focusedLegStage by remember { mutableStateOf<Int?>(null) }
    var stopsExpanded by remember(selectedVehicleMode, selectedBusRouteIndex) { mutableStateOf(false) }
    var showReminderPopup by remember { mutableStateOf(false) }
    var modeMapViewRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner, modeMapViewRef) {
        val map = modeMapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map?.onResume()
                Lifecycle.Event.ON_PAUSE -> map?.onPause()
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

    val selectedBusOption = bmtcRouteOptions.getOrNull(selectedBusRouteIndex)
    val activePlan: MetroJourneyPlan? = when {
        isBusMode -> selectedBusOption?.plan
        isMetroMode -> metroJourneyPlan
        else -> null
    }

    val roadLabel = if (radiusMeters >= 1000f) "${(radiusMeters / 1000f).toInt()} km" else "${radiusMeters.toInt()}m"
    val triggerLabel = if (isTransitMode) {
        if (metroStopsBeforeAlert == 0) "At stop" else "$metroStopsBeforeAlert ${if (metroStopsBeforeAlert == 1) "stop" else "stops"}"
    } else roadLabel

    Box(modifier = modifier.fillMaxSize().background(p.bg)) {
        // 1. Full-Screen Interactive OsmDroid Map
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(13.2)
                    controller.setCenter(targetLocation)
                    modeMapViewRef = this
                }
            },
            update = { map ->
                LocationMarkerUtils.applyDarkMapFilter(map, isDark)
                val renderKey = listOf(
                    selectedVehicleMode.name,
                    selectedBusRouteIndex,
                    focusedLegStage,
                    metroStopsBeforeAlert,
                    previewRoadRoute?.points?.size ?: 0,
                    boardingRoadRoute?.points?.size ?: 0,
                    exitRoadRoute?.points?.size ?: 0,
                    busLegRoadPoints.values.sumOf { it.size },
                    activePlan?.boardingStation?.id,
                    activePlan?.exitStation?.id,
                    activePlan?.totalStops,
                    isDark
                ).joinToString("|")

                if (map.tag == renderKey) return@AndroidView
                map.tag = renderKey

                map.overlays.clear()
                val focusPts = mutableListOf<GeoPoint>()

                if (isTransitMode && activePlan != null) {
                    val show1 = focusedLegStage == null || focusedLegStage == 0
                    val show2 = focusedLegStage == null || focusedLegStage == 1
                    val show3 = focusedLegStage == null || focusedLegStage == 2

                    if (focusedLegStage == null) {
                        focusPts.addAll(
                            LocationMarkerUtils.renderMetroJourneyOverlays(
                                map = map,
                                context = map.context,
                                plan = activePlan,
                                boardingRoadPoints = boardingRoadRoute?.points.orEmpty(),
                                stopsBeforeAlert = metroStopsBeforeAlert,
                                exitRoadPoints = exitRoadRoute?.points.orEmpty(),
                                busLegRoadPoints = if (activePlan.isBusJourney) busLegRoadPoints else emptyMap()
                            )
                        )
                    } else {
                        if (show1) {
                            val pts1 = boardingRoadRoute?.points.orEmpty().ifEmpty {
                                listOfNotNull(currentOriginLoc, activePlan.boardingStation.geoPoint)
                            }
                            if (pts1.size >= 2) {
                                map.overlays.add(Polyline(map).apply {
                                    setPoints(pts1)
                                    outlinePaint.color = p.orange.toArgb()
                                    outlinePaint.strokeWidth = 10f
                                })
                                focusPts.addAll(pts1)
                            }
                        }
                        if (show2) {
                            activePlan.legs.forEachIndexed { idx, leg ->
                                val rawLeg = if (leg.isBusLeg) busLegRoadPoints[idx] else null
                                val pts = if (!rawLeg.isNullOrEmpty()) rawLeg else leg.trackPoints
                                if (pts.size >= 2) {
                                    map.overlays.add(Polyline(map).apply {
                                        setPoints(pts)
                                        outlinePaint.color = if (leg.isBusLeg) p.blue.toArgb() else leg.lineColorInt
                                        outlinePaint.strokeWidth = 11f
                                    })
                                    focusPts.addAll(pts)
                                }
                            }
                        }
                        if (show3) {
                            val pts3 = exitRoadRoute?.points.orEmpty().ifEmpty {
                                listOf(activePlan.exitStation.geoPoint, activePlan.destinationPinPoint)
                            }
                            if (pts3.size >= 2) {
                                map.overlays.add(Polyline(map).apply {
                                    setPoints(pts3)
                                    outlinePaint.color = p.orange.toArgb()
                                    outlinePaint.strokeWidth = 10f
                                })
                                focusPts.addAll(pts3)
                            }
                        }
                    }

                    val anchorY = LocationMarkerUtils.getStationMarkerAnchorY(map.context)
                    if (show1 || show2) {
                        map.overlays.add(Marker(map).apply {
                            position = activePlan.boardingStation.geoPoint
                            title = activePlan.boardingStation.name
                            icon = if (activePlan.isBusJourney) {
                                LocationMarkerUtils.createBmtcCompactStopDrawable(map.context, activePlan.boardingStation.name, isSelected = true)
                            } else {
                                LocationMarkerUtils.createStationMarkerDrawable(map.context, activePlan.boardingStation.name, NammaMetroNetwork.getLineColor(activePlan.legs.firstOrNull()?.lineName))
                            }
                            setAnchor(Marker.ANCHOR_CENTER, anchorY)
                        })
                    }
                    if (show2) {
                        activePlan.interchangeStations.forEach { inter ->
                            map.overlays.add(Marker(map).apply {
                                position = inter.geoPoint
                                title = inter.name
                                icon = LocationMarkerUtils.createStationMarkerDrawable(map.context, inter.name, NammaMetroNetwork.COLOR_INTERCHANGE, isInterchange = true)
                                setAnchor(Marker.ANCHOR_CENTER, anchorY)
                            })
                        }
                    }
                    if ((show2 || show3) && activePlan.exitStation.id != activePlan.boardingStation.id) {
                        map.overlays.add(Marker(map).apply {
                            position = activePlan.exitStation.geoPoint
                            title = activePlan.exitStation.name
                            icon = if (activePlan.isBusJourney) {
                                LocationMarkerUtils.createBmtcCompactStopDrawable(map.context, activePlan.exitStation.name)
                            } else {
                                LocationMarkerUtils.createStationMarkerDrawable(map.context, activePlan.exitStation.name, NammaMetroNetwork.getLineColor(activePlan.legs.lastOrNull()?.lineName))
                            }
                            setAnchor(Marker.ANCHOR_CENTER, anchorY)
                        })
                    }
                    if (show3) {
                        map.overlays.add(Marker(map).apply {
                            position = activePlan.destinationPinPoint
                            title = activePlan.destinationPinName
                            icon = LocationMarkerUtils.createModernPinDrawable(map.context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                } else {
                    val routePts = previewRoadRoute?.points.orEmpty()
                    LocationMarkerUtils.updateRoadAndAlertOverlays(
                        map = map,
                        vehicleMode = selectedVehicleMode,
                        targetLocation = targetLocation,
                        alertDistanceMeters = radiusMeters,
                        primaryRoutePoints = routePts,
                        themeColorInt = p.orange.toArgb(),
                        targetName = targetName
                    )
                    map.overlays.add(Marker(map).apply {
                        position = targetLocation
                        title = targetName
                        icon = LocationMarkerUtils.createModernPinDrawable(map.context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    })
                    focusPts.addAll(routePts)
                    if (focusPts.isEmpty()) focusPts.add(targetLocation)
                }

                if (focusPts.size >= 2) {
                    map.post {
                        runCatching {
                            val box = BoundingBox.fromGeoPoints(focusPts)
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
                } else {
                    map.controller.setCenter(focusPts.firstOrNull() ?: targetLocation)
                }
                map.invalidate()
            }
        )

        // 2. Top Safe-Area Direction Cue Banner
        val (cueArrow, cueMain, cueSub) = setupDirectionCue(
            mode = selectedVehicleMode,
            plan = activePlan,
            targetName = targetName,
            roadDistMeters = previewRoadRoute?.distanceMeters,
            roadLabel = roadLabel,
            focusedLegStage = focusedLegStage
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
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(WakeMeScreen.SEARCH_LANDING) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = p.cueInk, modifier = Modifier.size(18.dp))
                }
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
                if (focusedLegStage != null) {
                    Surface(
                        onClick = { focusedLegStage = null },
                        shape = RoundedCornerShape(999.dp),
                        color = p.orange
                    ) {
                        Text(
                            "Show All",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 3. Persistent Bottom Sheet (ui_preview layout)
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

                Text("Your Location", color = p.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val headline = when {
                    isMetroMode && activePlan != null -> "$targetName • ${activePlan.totalStops} Metro Stops"
                    isBusMode && selectedBusOption != null -> "$targetName • ${selectedBusOption.routeTitle}"
                    else -> "$targetName • Direct Road"
                }
                Text(headline, color = p.ink, fontSize = 17.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

                // Segmented Mode Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(p.bg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        VehicleMode.METRO to "Namma Metro",
                        VehicleMode.BUS to "City Bus",
                        VehicleMode.CAR to "Direct Road"
                    ).forEach { (vmMode, label) ->
                        val selected = selectedVehicleMode == vmMode
                        Surface(
                            onClick = {
                                focusedLegStage = null
                                viewModel.setVehicleMode(vmMode)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) p.surface else Color.Transparent,
                            shadowElevation = if (selected) 2.dp else 0.dp
                        ) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                                Text(label, color = if (selected) p.ink else p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }

                // Clean 2-Row BMTC Route Cards
                AnimatedVisibility(
                    visible = isBusMode && bmtcRouteOptions.isNotEmpty() && focusedLegStage == null,
                    enter = expandVertically(tween(WakeMeMotion.DURATION_MS)) + fadeIn(),
                    exit = shrinkVertically(tween(WakeMeMotion.DURATION_MS)) + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bmtcRouteOptions.forEachIndexed { idx, opt ->
                            val sel = idx == selectedBusRouteIndex
                            Surface(
                                onClick = { viewModel.selectBusRouteOption(idx) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) p.blue.copy(alpha = 0.12f) else p.bg,
                                border = BorderStroke(if (sel) 1.5.dp else 1.dp, if (sel) p.blue else p.hairline)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(210.dp)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        opt.plan.legs.forEachIndexed { legIdx, leg ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(p.blue)
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(leg.lineName, color = p.badgeInk, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                            if (legIdx < opt.plan.legs.lastIndex) {
                                                Text("›", color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${opt.plan.legs.size} ${if (opt.plan.legs.size == 1) "bus" else "buses"} • ${opt.plan.totalStops} stops",
                                            color = p.ink,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(opt.frequencyText.ifBlank { opt.subtitle }, color = p.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3-Stage Interactive Timeline (or Direct Road summary)
                if (isTransitMode && activePlan != null) {
                    val walkMeters = boardingRoadRoute?.distanceMeters?.toInt() ?: 250
                    val walkMins = ((boardingRoadRoute?.durationSeconds ?: 180f) / 60f).toInt().coerceAtLeast(1)
                    val exitMeters = exitRoadRoute?.distanceMeters ?: activePlan.exitWalkDistanceMeters
                    val exitLabel = if (exitMeters >= 1000f) String.format(Locale.US, "%.1f km", exitMeters / 1000f) else "${exitMeters.toInt()}m"

                    SetupTimelineRow(
                        palette = p,
                        dotColor = p.orange,
                        title = "1. Reach ${activePlan.boardingStation.name}",
                        subtitle = "Tap for map • Switches at 50m",
                        value = "${walkMeters}m • ${walkMins}m",
                        isOrange = true,
                        isFocused = focusedLegStage == 0,
                        onClick = { focusedLegStage = if (focusedLegStage == 0) null else 0 },
                        onLongClick = { focusedLegStage = if (focusedLegStage == 0) null else 0 }
                    )
                    HorizontalDivider(color = p.hairline)

                    val legChain = activePlan.legs.joinToString(" ➔ ") { it.lineName }
                    val alertStopName = activePlan.getAlertStopForLeg(activePlan.legs.lastIndex, metroStopsBeforeAlert)?.name ?: activePlan.exitStation.name
                    SetupTimelineRow(
                        palette = p,
                        dotColor = if (isBusMode) p.blue else p.purple,
                        title = "2. $legChain",
                        subtitle = if (stopsExpanded) "Tap for map • Alert at $alertStopName" else "Tap for map • Hold for stops • Alert $triggerLabel before",
                        value = "${activePlan.totalStops} stops",
                        isOrange = false,
                        isFocused = focusedLegStage == 1,
                        trailingIcon = if (stopsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        onClick = { focusedLegStage = if (focusedLegStage == 1) null else 1 },
                        onLongClick = { stopsExpanded = !stopsExpanded }
                    )
                    AnimatedVisibility(
                        visible = stopsExpanded,
                        enter = expandVertically(tween(WakeMeMotion.DURATION_MS)) + fadeIn(),
                        exit = shrinkVertically(tween(WakeMeMotion.DURATION_MS)) + fadeOut()
                    ) {
                        val allStops = activePlan.legs.flatMapIndexed { legIdx, leg ->
                            val list = if (legIdx == 0) leg.stations else leg.stations.drop(1)
                            val alertStop = activePlan.getAlertStopForLeg(legIdx, metroStopsBeforeAlert)
                            list.map { st ->
                                val isInter = activePlan.interchangeStations.any { it.id == st.id }
                                val col = when {
                                    alertStop?.id == st.id -> p.red
                                    isInter -> p.blue
                                    leg.isBusLeg -> p.blue
                                    else -> Color(leg.lineColorInt)
                                }
                                Triple(st.name, col, alertStop?.id == st.id)
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 148.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(p.bg)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allStops.forEachIndexed { idx, (name, dotCol, isAlert) ->
                                val isKey = idx == 0 || idx == allStops.lastIndex || isAlert
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.size(if (isKey) 8.dp else 5.5.dp).clip(CircleShape).background(dotCol))
                                    Text(
                                        text = if (isAlert) "$name (Alert)" else name,
                                        color = if (isKey) p.ink else p.muted,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isKey) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = p.hairline)

                    SetupTimelineRow(
                        palette = p,
                        dotColor = p.orange,
                        title = "3. ${activePlan.exitStation.name} ➔ $targetName",
                        subtitle = "Tap for final road path • Alerts at 150m",
                        value = exitLabel,
                        isOrange = true,
                        isFocused = focusedLegStage == 2,
                        onClick = { focusedLegStage = if (focusedLegStage == 2) null else 2 },
                        onLongClick = { focusedLegStage = if (focusedLegStage == 2) null else 2 }
                    )
                } else {
                    val distStr = previewRoadRoute?.let { String.format(Locale.US, "%.1f km", it.distanceMeters / 1000f) } ?: if (isLoadingPreviewRoute) "Calculating..." else "Road Route"
                    val etaStr = previewRoadRoute?.let { "${(it.durationSeconds / 60f).toInt().coerceAtLeast(1)}m" } ?: ""
                    SetupTimelineRow(
                        palette = p,
                        dotColor = p.blue,
                        title = if (isTransitMode) "No Direct ${if (isMetroMode) "Metro" else "Bus"} Found" else "Real-Road Route",
                        subtitle = if (isTransitMode) "Falling back to Direct Road tracking" else "Tracks along road polyline",
                        value = if (etaStr.isNotEmpty()) "$distStr • $etaStr" else distStr,
                        isOrange = false,
                        isFocused = false,
                        onClick = {},
                        onLongClick = {}
                    )
                    HorizontalDivider(color = p.hairline)
                    SetupTimelineRow(
                        palette = p,
                        dotColor = p.orange,
                        title = "Road Alert Threshold",
                        subtitle = "Gentle reminder fires when $roadLabel remains",
                        value = roadLabel,
                        isOrange = true,
                        isFocused = false,
                        onClick = { showReminderPopup = true },
                        onLongClick = { showReminderPopup = true }
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Bottom CTA Row: Reminder Bell Pill (left) + Start Journey (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showReminderPopup = true },
                        shape = RoundedCornerShape(14.dp),
                        color = p.bg,
                        border = BorderStroke(1.dp, p.hairline)
                    ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alert settings", tint = p.orange, modifier = Modifier.size(17.dp))
                            Text(triggerLabel, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            WakeMeAlarmController.stopAlert(context)
                            viewModel.startTracking(context)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.ctaBg, contentColor = p.ctaInk)
                    ) {
                        val actionLabel = when {
                            isMetroMode -> "Start Metro ($triggerLabel)"
                            isBusMode -> "Start Bus ($triggerLabel)"
                            else -> "Start Road ($triggerLabel)"
                        }
                        Text(actionLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showReminderPopup) {
        AlertDialog(
            onDismissRequest = { showReminderPopup = false },
            title = { Text("Reminder & Ring Style", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isTransitMode) "Stops Before Exit / Transfer" else "Alert Distance Before Destination",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        if (isTransitMode) {
                            listOf(0 to "At stop", 1 to "1 stop", 2 to "2 stops", 3 to "3 stops").forEach { (stops, lbl) ->
                                FilterChip(
                                    selected = metroStopsBeforeAlert == stops,
                                    onClick = { viewModel.setMetroStopsBeforeAlert(stops) },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        } else {
                            listOf(200f to "200m", 500f to "500m", 1000f to "1 km", 2000f to "2 km").forEach { (m, lbl) ->
                                FilterChip(
                                    selected = radiusMeters.toInt() == m.toInt(),
                                    onClick = { viewModel.setRadius(m) },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    Text(
                        text = "How It Rings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        AudioRouting.values().forEach { routing ->
                            FilterChip(
                                selected = audioRouting == routing,
                                onClick = { viewModel.setAudioRouting(routing) },
                                label = { Text(routing.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vibrate with Alert", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = vibrateEnabled, onCheckedChange = { viewModel.setVibrateEnabled(it) })
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showReminderPopup = false }) { Text("Done") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetupTimelineRow(
    palette: com.wakeme.app.ui.theme.WakeMePalette,
    dotColor: Color,
    title: String,
    subtitle: String,
    value: String,
    isOrange: Boolean,
    isFocused: Boolean,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) dotColor.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = if (isFocused) 6.dp else 0.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(dotColor))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = palette.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                color = if (isOrange) palette.orange else palette.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = palette.muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun setupDirectionCue(
    mode: VehicleMode,
    plan: MetroJourneyPlan?,
    targetName: String,
    roadDistMeters: Float?,
    roadLabel: String,
    focusedLegStage: Int?
): Triple<String, String, String> {
    if (focusedLegStage != null && plan != null) {
        return when (focusedLegStage) {
            0 -> Triple("↗", "Stage 1: Road to Boarding", "Reach ${plan.boardingStation.name}")
            1 -> Triple(if (plan.isBusJourney) "B" else "M", "Stage 2: Transit Corridor", "${plan.legs.joinToString(" ➔ ") { it.lineName }} (${plan.totalStops} stops)")
            else -> Triple("↑", "Stage 3: Final Road Path", "${plan.exitStation.name} ➔ $targetName")
        }
    }
    if (plan != null) {
        return Triple("↗", "Walk to ${plan.boardingStation.name}", "${plan.legs.joinToString(" ➔ ") { it.lineName }} • ${plan.totalStops} stops")
    }
    val distStr = roadDistMeters?.let { String.format(Locale.US, "%.1f km", it / 1000f) } ?: "Direct Road"
    return Triple("↑", "$distStr to $targetName", "Gentle reminder armed at $roadLabel")
}
