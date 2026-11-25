package com.wakeme.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wakeme.app.alarm.WakeMeAlarmController
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.model.VehicleMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.util.LocationMarkerUtils
import java.util.Locale
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Screen 2: Mode Setup Screen ("After search & I select a place").
 * Faithful implementation of user's notebook sketch:
 * - Top: Origin & Destination route card (• your location | • destination).
 * - Middle: Interactive Route Map with destination marker and dynamic geofence radius circle.
 * - Bottom:
 *   - Travel Mode 3-button row side-by-side: [ 🚌 City Bus ] [ 🚇 Namma Metro ] [ 🚗 Direct Road ].
 *   - Wake-Up Radius: [ 200m ] [ 500m ] [ 1 km ] [ 2 km ] + [ Custom... ].
 *   - Audio options: [ 🔊 Speaker ] [ 🔔 Both ] [ 🎧 Headphones ] + [ 📳 Vibrate (+/-) ].
 *   - Details about trip summary.
 *   - Prominent [ START COMMUTE ALARM ] CTA button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeMeModeSelectionScreen(
    viewModel: WakeMeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
    val allBusStopsList by viewModel.allBusStopsList.collectAsState()
    val boardingRoadRoute by viewModel.boardingRoadRoute.collectAsState()
    val exitRoadRoute by viewModel.exitRoadRoute.collectAsState()

    val isRoadMode = selectedVehicleMode.usesRoadDistanceTrigger
    val isMetroMode = selectedVehicleMode == VehicleMode.METRO
    val isBusMode = selectedVehicleMode == VehicleMode.BUS
    val isTransitMode = isMetroMode || isBusMode

    var showCustomRadiusDialog by remember { mutableStateOf(false) }
    var showBoardingStationPicker by remember { mutableStateOf(false) }

    LaunchedEffect(targetLocation, selectedVehicleMode) {
        viewModel.refreshPreviewRoadRoute()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trip Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = targetName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(WakeMeScreen.SEARCH_LANDING) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Big Bottom Start CTA Button (from sketch: start button)
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
                    val triggerLabel = if (isTransitMode) {
                        if (metroStopsBeforeAlert == 0) {
                            "At ${if (isBusMode) "Stop" else "Station"} • 50m"
                        } else {
                            "$metroStopsBeforeAlert stop${if (metroStopsBeforeAlert > 1) "s" else ""} before"
                        }
                    } else if (radiusMeters >= 1000f) {
                        "${(radiusMeters / 1000f).toInt()} km"
                    } else {
                        "${radiusMeters.toInt()}m"
                    }

                    Button(
                        onClick = {
                            WakeMeAlarmController.stopAlert(context)
                            viewModel.startTracking(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        val actionLabel = when {
                            isMetroMode -> "START METRO JOURNEY"
                            isBusMode -> "START BUS JOURNEY"
                            isRoadMode -> "START ROAD ALARM"
                            else -> "START COMMUTE ALARM"
                        }
                        Text(
                            text = "${selectedVehicleMode.iconEmoji} $actionLabel ($triggerLabel)",
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Route Connection Card (from sketch: • your location | • destination)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Origin row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32)) // Green origin dot
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your location",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Route connector vertical line
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .width(2.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Destination row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = targetName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val orangeColor = Color(0xFFE65100)
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

                            val annotatedSummary = buildAnnotatedString {
                                when {
                                    isLoadingPreviewRoute -> {
                                        withStyle(SpanStyle(color = mutedColor)) {
                                            append("Calculating route...")
                                        }
                                    }
                                    isTransitMode && metroJourneyPlan != null -> {
                                        val p = metroJourneyPlan!!
                                        val boardRoad = boardingRoadRoute
                                        if (boardRoad != null) {
                                            val rd = if (boardRoad.distanceMeters >= 1000f) String.format(Locale.US, "%.1f km", boardRoad.distanceMeters / 1000f) else "${boardRoad.distanceMeters.toInt()}m"
                                            val bMin = (boardRoad.durationSeconds / 60).toInt().coerceAtLeast(1)
                                            withStyle(SpanStyle(color = orangeColor, fontWeight = FontWeight.Bold)) {
                                                append("🛣️ $rd (~$bMin min) to ${p.boardingStation.name}")
                                            }
                                            append(" • ")
                                        } else {
                                            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                                                append("📍 At ${p.boardingStation.name} (≤50m)")
                                            }
                                            append(" • ")
                                        }

                                        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                                            if (p.isBusJourney) {
                                                val routesStr = p.legs.joinToString(" ➔ ") { it.lineName }
                                                append("🚌 $routesStr (${p.totalStops} stops)")
                                            } else {
                                                append("${p.totalStops} metro stops")
                                            }
                                            if (p.requiresLineChange) {
                                                append(" • 🔄 Change at ${p.interchangeStations.joinToString { it.name }}")
                                            }
                                        }

                                        if (p.exitWalkDistanceMeters > 50f) {
                                            val exitRoad = exitRoadRoute
                                            val ed = exitRoad?.distanceMeters ?: (p.exitWalkDistanceMeters * 1.28f)
                                            val eMin = ((exitRoad?.durationSeconds ?: (ed / 8.33f)) / 60).toInt().coerceAtLeast(1)
                                            val edText = if (ed >= 1000f) String.format(Locale.US, "%.1f km", ed / 1000f) else "${ed.toInt()}m"
                                            append(" • ")
                                            withStyle(SpanStyle(color = orangeColor, fontWeight = FontWeight.Bold)) {
                                                append("🛣️ $edText (~$eMin min) to Point B")
                                            }
                                        }
                                    }
                                    previewRoadRoute != null -> {
                                        val r = previewRoadRoute!!
                                        val dText = if (r.distanceMeters >= 1000f) {
                                            String.format(Locale.US, "%.1f km", r.distanceMeters / 1000f)
                                        } else {
                                            "${r.distanceMeters.toInt()} m"
                                        }
                                        val minText = (r.durationSeconds / 60).toInt().coerceAtLeast(1)
                                        withStyle(SpanStyle(color = orangeColor, fontWeight = FontWeight.Bold)) {
                                            append("🛣️ $dText by road • ~$minText min")
                                        }
                                    }
                                    else -> {
                                        withStyle(SpanStyle(color = mutedColor)) {
                                            append(String.format("%.4f° N, %.4f° E", targetLocation.latitude, targetLocation.longitude))
                                        }
                                    }
                                }
                            }
                            Text(
                                text = annotatedSummary,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Stop-to-Stop Boarding Selector Pill & Matching BMTC Bus Route Chips
                    if (isTransitMode && metroJourneyPlan != null) {
                        val p = metroJourneyPlan!!
                        val isCustomStart = if (isBusMode) customBoardingBusStop != null else customBoardingStation != null
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Board: ${p.boardingStation.name} ${if (!isCustomStart) "(Nearest)" else "(Custom)"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = p.legs.joinToString(" ➔ ") { "${it.lineName} (${it.stopsCount} stops)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showBoardingStationPicker = true }) {
                                Text("Change Start ▾", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isBusMode && bmtcRouteOptions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                bmtcRouteOptions.forEachIndexed { idx, opt ->
                                    val isSel = idx == selectedBusRouteIndex
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { viewModel.selectBusRouteOption(idx) },
                                        label = {
                                            Text(
                                                text = if (opt.isTransferRoute) "🔄 ${opt.routeTitle} (${opt.plan.totalStops})" else "🚌 ${opt.routeTitle} (${opt.plan.totalStops})",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Middle Route MAP (from sketch: + Route MAP)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(14.5)
                            controller.setCenter(targetLocation)
                        }
                    },
                    update = { map ->
                        val isBusMode = selectedVehicleMode == VehicleMode.BUS
                        val isMetroSelected = selectedVehicleMode == VehicleMode.METRO
                        val themeColorInt = when {
                            isBusMode -> selectedBusCategory.primaryColorInt
                            isMetroSelected -> NammaMetroNetwork.COLOR_PURPLE
                            else -> 0xFF1E88E5.toInt()
                        }

                        map.overlays.removeAll(map.overlays.filterIsInstance<Marker>())
                        val plan = metroJourneyPlan

                        if (isTransitMode && plan != null) {
                            val zoomPts = LocationMarkerUtils.renderMetroJourneyOverlays(
                                map = map,
                                context = map.context,
                                plan = plan,
                                boardingRoadPoints = boardingRoadRoute?.points.orEmpty(),
                                stopsBeforeAlert = metroStopsBeforeAlert,
                                exitRoadPoints = exitRoadRoute?.points.orEmpty(),
                                busLegRoadPoints = if (plan.isBusJourney) busLegRoadPoints else emptyMap()
                            )

                            val anchorY = LocationMarkerUtils.getStationMarkerAnchorY(map.context)
                            // Boarding station / bus stop marker
                            map.overlays.add(Marker(map).apply {
                                position = plan.boardingStation.geoPoint
                                title = "Board: ${plan.boardingStation.name}"
                                icon = if (plan.isBusJourney) {
                                    LocationMarkerUtils.createBmtcCompactStopDrawable(
                                        map.context,
                                        "Board: ${plan.boardingStation.name}",
                                        isSelected = true
                                    )
                                } else {
                                    LocationMarkerUtils.createStationMarkerDrawable(
                                        map.context,
                                        "Board: ${plan.boardingStation.name}",
                                        NammaMetroNetwork.getLineColor(plan.legs.firstOrNull()?.lineName)
                                    )
                                }
                                setAnchor(Marker.ANCHOR_CENTER, anchorY)
                            })
                            // Interchange station / bus transfer hub markers
                            for (inter in plan.interchangeStations) {
                                map.overlays.add(Marker(map).apply {
                                    position = inter.geoPoint
                                    title = "Change at ${inter.name}"
                                    icon = LocationMarkerUtils.createStationMarkerDrawable(
                                        map.context,
                                        "🔄 ${inter.name}",
                                        NammaMetroNetwork.COLOR_INTERCHANGE,
                                        isInterchange = true
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, anchorY)
                                })
                            }
                            // Exit station / bus stop marker
                            if (plan.exitStation.id != plan.boardingStation.id) {
                                map.overlays.add(Marker(map).apply {
                                    position = plan.exitStation.geoPoint
                                    title = "Exit: ${plan.exitStation.name}"
                                    icon = if (plan.isBusJourney) {
                                        LocationMarkerUtils.createBmtcCompactStopDrawable(
                                            map.context,
                                            plan.exitStation.name
                                        )
                                    } else {
                                        LocationMarkerUtils.createStationMarkerDrawable(
                                            map.context,
                                            plan.exitStation.name,
                                            NammaMetroNetwork.getLineColor(plan.legs.lastOrNull()?.lineName)
                                        )
                                    }
                                    setAnchor(Marker.ANCHOR_CENTER, anchorY)
                                })
                            }
                            // Final destination pin marker when > 50m beyond exit Metro station
                            if (plan.exitWalkDistanceMeters > 50f) {
                                map.overlays.add(Marker(map).apply {
                                    position = plan.destinationPinPoint
                                    title = plan.destinationPinName
                                    icon = LocationMarkerUtils.createModernPinDrawable(map.context)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                })
                            }

                            if (zoomPts.size >= 2) {
                                map.post {
                                    try {
                                        map.zoomToBoundingBox(BoundingBox.fromGeoPoints(zoomPts), true, 64)
                                    } catch (_: Exception) {
                                        map.controller.setCenter(targetLocation)
                                    }
                                }
                            } else {
                                map.controller.setCenter(targetLocation)
                            }
                        } else {
                            val routePts = if (selectedVehicleMode.usesRoadDistanceTrigger) {
                                previewRoadRoute?.points.orEmpty()
                            } else {
                                emptyList()
                            }

                            LocationMarkerUtils.updateRoadAndAlertOverlays(
                                map = map,
                                vehicleMode = selectedVehicleMode,
                                targetLocation = targetLocation,
                                alertDistanceMeters = radiusMeters,
                                primaryRoutePoints = routePts,
                                themeColorInt = themeColorInt,
                                targetName = targetName
                            )

                            val destMarker = Marker(map).apply {
                                position = targetLocation
                                title = targetName
                                icon = when {
                                    isBusMode -> LocationMarkerUtils.createTwoToneBusBadgeDrawable(map.context, selectedBusCategory, sizeDp = 30)
                                    isMetroSelected -> LocationMarkerUtils.createStationMarkerDrawable(map.context, targetName, NammaMetroNetwork.COLOR_PURPLE)
                                    else -> LocationMarkerUtils.createModernPinDrawable(map.context)
                                }
                                setAnchor(Marker.ANCHOR_CENTER, if (isBusMode) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(destMarker)

                            if (selectedVehicleMode.usesRoadDistanceTrigger && routePts.size >= 2) {
                                map.post {
                                    try {
                                        map.zoomToBoundingBox(BoundingBox.fromGeoPoints(routePts), true, 60)
                                    } catch (_: Exception) {
                                        map.controller.setCenter(targetLocation)
                                    }
                                }
                            } else {
                                map.controller.setCenter(targetLocation)
                            }
                        }
                        map.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Travel Mode Selector: 3-Button Row Side-by-Side (from sketch: [Bus] [Metro] [Direction])
            Text(
                text = "TRAVEL MODE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metro Button (Primary Mode)
                val isMetro = selectedVehicleMode == VehicleMode.METRO
                Surface(
                    onClick = { viewModel.setVehicleMode(VehicleMode.METRO) },
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isMetro) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isMetro) 2.dp else 1.dp,
                        if (isMetro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚇", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Namma Metro", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Active Lines", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Direct Road Button
                val isCar = selectedVehicleMode == VehicleMode.CAR
                Surface(
                    onClick = { viewModel.setVehicleMode(VehicleMode.CAR) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCar) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isCar) 2.dp else 1.dp,
                        if (isCar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚗", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Direct Road", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Cab / Auto", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // City Bus Button (Active BMTC Network)
                val isBus = selectedVehicleMode == VehicleMode.BUS
                Surface(
                    onClick = { viewModel.setVehicleMode(VehicleMode.BUS) },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isBus) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isBus) 2.dp else 1.dp,
                        if (isBus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚌", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("City Bus", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("BMTC Routes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 3b. BMTC Bus 50/50 Livery Divisions (Dark Blue AC / Normal Blue Sarige / Light Blue EV)
            AnimatedVisibility(visible = selectedVehicleMode == VehicleMode.BUS) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "BMTC BUS DIVISION (50/50 LIVERY)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = selectedBusCategory.composeColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BmtcBusCategory.values().forEach { cat ->
                            val isSelected = selectedBusCategory == cat
                            Surface(
                                onClick = { viewModel.setBusCategory(cat) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) cat.composeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) cat.composeColor else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    // 50/50 two-tone visual indicator dot
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(cat.composeColor)
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = cat.shortCode,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) cat.composeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Alert Threshold: Stops Before (Metro & BMTC Bus), Road Distance (Cab/Auto)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when {
                        isTransitMode -> "WAKE-UP ALERT (STOPS BEFORE)"
                        isRoadMode -> "ALERT DISTANCE BY ROAD"
                        else -> "WAKE-UP RADIUS"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isTransitMode) {
                    Text(
                        text = if (isBusMode) {
                            "Orange road path if >50m from stop; wakes you at chosen stop before exit & transfer."
                        } else {
                            "Shows road path if >50m from Metro; wakes you at chosen stop before exit & line change."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isRoadMode) {
                    Text(
                        text = "Reminders follow real driving roads (no flyover or divider false alarms).",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isTransitMode) {
                val stopOptions = listOf(1, 2, 3, 0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stopOptions.forEach { stopCount ->
                        val isSelected = (metroStopsBeforeAlert == stopCount)
                        val label = when (stopCount) {
                            0 -> if (isBusMode) "At Stop (50m)" else "At Station (50m)"
                            1 -> "1 Stop Before"
                            else -> "$stopCount Stops Before"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setMetroStopsBeforeAlert(stopCount) },
                            label = {
                                Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            } else {
                val radiusPresets = listOf(200f, 500f, 1000f, 2000f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    radiusPresets.forEach { preset ->
                        val isSelected = (radiusMeters == preset)
                        val label = if (preset >= 1000f) "${(preset / 1000f).toInt()} km" else "${preset.toInt()}m"
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setRadius(preset) },
                            label = {
                                Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    // Custom Radius Chip
                    val isCustom = radiusPresets.none { it == radiusMeters }
                    val customLabel = if (isCustom) {
                        if (radiusMeters >= 1000f) "${(radiusMeters / 1000f).toInt()} km (Custom)" else "${radiusMeters.toInt()}m (Custom)"
                    } else {
                        "Custom..."
                    }

                    FilterChip(
                        selected = isCustom,
                        onClick = { showCustomRadiusDialog = true },
                        label = {
                            Text(customLabel, fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            // 5. Audio Output & Vibration Options (from sketch: speaker [Audio option] (option), vibrate (+/-))
            Text(
                text = "AUDIO & ALERT OPTIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioRouting.values().forEach { routing ->
                    val isSelected = (audioRouting == routing)
                    val label = when (routing) {
                        AudioRouting.SPEAKER_ONLY -> "🔊 Speaker"
                        AudioRouting.BOTH -> "🔔 Both"
                        AudioRouting.HEADPHONES_ONLY -> "🎧 Headphones"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setAudioRouting(routing) },
                        label = {
                            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Vibration (+ / -) Toggle Chip
            FilterChip(
                selected = vibrateEnabled,
                onClick = { viewModel.setVibrateEnabled(!vibrateEnabled) },
                label = {
                    Text(
                        text = if (vibrateEnabled) "📳 Vibration: ENABLED (+)" else "📳 Vibration: DISABLED (-)",
                        fontWeight = if (vibrateEnabled) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (vibrateEnabled) Icons.Default.Vibration else Icons.Default.VolumeMute,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 6. Details About Trip (from sketch: Details about trip)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val radiusLabel = if (radiusMeters >= 1000f) "${(radiusMeters / 1000f).toInt()} km" else "${radiusMeters.toInt()}m"
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trip Summary",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val summaryText = when {
                            isTransitMode && metroJourneyPlan != null -> {
                                val p = metroJourneyPlan!!
                                val lastLegIdx = (p.legs.size - 1).coerceAtLeast(0)
                                val alertStop = p.getAlertStopForLeg(lastLegIdx, metroStopsBeforeAlert)
                                val finalDesc = if (metroStopsBeforeAlert == 0) {
                                    "Wake-up siren fires at ${p.exitStation.name} (50m)"
                                } else {
                                    "Wake-up siren fires at ${alertStop?.name ?: p.exitStation.name} ($metroStopsBeforeAlert stop${if (metroStopsBeforeAlert > 1) "s" else ""} before ${p.exitStation.name})"
                                }
                                val stage3Desc = if (p.exitWalkDistanceMeters > 50f) {
                                    " • Then Orange road path to $targetName"
                                } else ""
                                if (p.requiresLineChange) {
                                    val interNames = p.interchangeStations.joinToString { it.name }
                                    "Transfer alert before $interNames • $finalDesc$stage3Desc"
                                } else {
                                    "$finalDesc$stage3Desc"
                                }
                            }
                            isRoadMode -> "Reminder fires when $radiusLabel of road route remains to $targetName"
                            else -> "Alarm activates at $radiusLabel radius before $targetName"
                        }
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(selectedVehicleMode.iconEmoji, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Custom Radius Dialog
    if (showCustomRadiusDialog) {
        var customInput by remember { mutableStateOf(radiusMeters.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { showCustomRadiusDialog = false },
            title = {
                Text(
                    if (isRoadMode) "Set Custom Road Alert Distance" else "Set Custom Wake-Up Radius",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isRoadMode) {
                            "Enter remaining road distance in meters (50m to 5,000m):"
                        } else {
                            "Enter radius distance in meters (50m to 5,000m):"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isRoadMode) "Road distance (meters)" else "Radius (meters)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = customInput.toFloatOrNull()
                        if (parsed != null && parsed >= 50f) {
                            viewModel.setRadius(parsed)
                        }
                        showCustomRadiusDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
                    dismissButton = {
                TextButton(onClick = { showCustomRadiusDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Searchable Starting / Boarding Metro Station or BMTC Bus Stop Picker
    if (showBoardingStationPicker) {
        var stationFilter by remember { mutableStateOf("") }
        val filteredStations = remember(stationFilter) {
            if (stationFilter.isBlank()) {
                NammaMetroNetwork.ACTIVE_STATIONS
            } else {
                NammaMetroNetwork.ACTIVE_STATIONS.filter {
                    it.name.contains(stationFilter, ignoreCase = true) ||
                        it.line.contains(stationFilter, ignoreCase = true)
                }
            }
        }
        val filteredBusStops = remember(stationFilter, allBusStopsList) {
            if (stationFilter.isBlank()) {
                allBusStopsList.take(50)
            } else {
                allBusStopsList.filter {
                    it.name.contains(stationFilter, ignoreCase = true) ||
                        it.ref.contains(stationFilter, ignoreCase = true) ||
                        it.directionHint.contains(stationFilter, ignoreCase = true)
                }.take(50)
            }
        }

        AlertDialog(
            onDismissRequest = { showBoardingStationPicker = false },
            title = {
                Text(
                    if (isBusMode) "Select Boarding BMTC Stop" else "Select Boarding Metro Station",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stationFilter,
                        onValueChange = { stationFilter = it },
                        placeholder = {
                            Text(if (isBusMode) "Search bus stop or route (e.g. 500-D)..." else "Search station or line...")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (isBusMode) viewModel.setCustomBoardingBusStop(null)
                                else viewModel.setCustomBoardingStation(null)
                                showBoardingStationPicker = false
                            },
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = if (isBusMode) "📍 Auto (Best Matching Nearby Stop)" else "📍 Auto (Nearest Metro by Road)",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isBusMode) {
                            items(filteredBusStops, key = { it.id }) { stop ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.setCustomBoardingBusStop(stop)
                                            showBoardingStationPicker = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stop.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (stop.directionHint.isNotBlank()) {
                                            Text(
                                                text = stop.directionHint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stop.ref.split(',').take(3).joinToString(","),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1565C0),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            items(filteredStations, key = { it.id }) { st ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.setCustomBoardingStation(st)
                                            showBoardingStationPicker = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = st.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = st.line,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(NammaMetroNetwork.getLineColor(st.line)),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBoardingStationPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}
