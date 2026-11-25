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
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        val (ctaIcon, actionLabel) = when {
                            isMetroMode -> Icons.Default.DirectionsSubway to "Start Metro Journey"
                            isBusMode -> Icons.Default.DirectionsBus to "Start Bus Journey"
                            isRoadMode -> Icons.Default.DirectionsCar to "Start Road Alarm"
                            else -> Icons.Default.NotificationsActive to "Start Commute Alarm"
                        }
                        Icon(
                            imageVector = ctaIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$actionLabel ($triggerLabel)",
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
                            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

                            if (!isTransitMode) {
                                val annotatedSummary = buildAnnotatedString {
                                    when {
                                        isLoadingPreviewRoute -> {
                                            withStyle(SpanStyle(color = mutedColor)) {
                                                append("Calculating route...")
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
                            } else if (isLoadingPreviewRoute && metroJourneyPlan == null) {
                                Text(
                                    text = if (isBusMode) "Finding best BMTC routes & opposite-side poles..." else "Finding best Namma Metro route...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = mutedColor
                                )
                            }
                        }
                    }

                    // Informative 3-Stage Stepper + Expandable Stops List + Matching BMTC Route Cards
                    if (isTransitMode && metroJourneyPlan != null) {
                        val p = metroJourneyPlan!!
                        val isCustomStart = if (isBusMode) customBoardingBusStop != null else customBoardingStation != null
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        if (isBusMode && bmtcRouteOptions.isNotEmpty()) {
                            BmtcRouteOptionCards(
                                options = bmtcRouteOptions,
                                selectedIndex = selectedBusRouteIndex,
                                onSelect = { idx ->
                                    viewModel.selectBusRouteOption(idx)
                                    bmtcRouteOptions.getOrNull(idx)?.let { opt ->
                                        viewModel.setBusCategory(opt.category)
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }

                        TransitThreeStageStepperCard(
                            plan = p,
                            boardingRoadRoute = boardingRoadRoute,
                            exitRoadRoute = exitRoadRoute,
                            stopsBeforeAlert = metroStopsBeforeAlert,
                            isCustomStart = isCustomStart,
                            isLoadingRoutes = isLoadingPreviewRoute,
                            onOpenStartPicker = { showBoardingStationPicker = true }
                        )
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

            // Travel Mode Selector
            Text(
                text = "Travel Mode",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isMetro = selectedVehicleMode == VehicleMode.METRO
                ModeSelectorCard(
                    title = "Namma Metro",
                    subtitle = "Active Lines",
                    icon = Icons.Default.DirectionsSubway,
                    selected = isMetro,
                    modifier = Modifier.weight(1.05f),
                    onClick = { viewModel.setVehicleMode(VehicleMode.METRO) }
                )

                val isCar = selectedVehicleMode == VehicleMode.CAR
                ModeSelectorCard(
                    title = "Direct Road",
                    subtitle = "Cab / Auto",
                    icon = Icons.Default.DirectionsCar,
                    selected = isCar,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setVehicleMode(VehicleMode.CAR) }
                )

                val isBus = selectedVehicleMode == VehicleMode.BUS
                ModeSelectorCard(
                    title = "City Bus",
                    subtitle = "BMTC Routes",
                    icon = Icons.Default.DirectionsBus,
                    selected = isBus,
                    modifier = Modifier.weight(0.95f),
                    onClick = { viewModel.setVehicleMode(VehicleMode.BUS) }
                )
            }

            // Alert Threshold: Stops Before (Metro & BMTC Bus), Road Distance (Cab/Auto)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when {
                        isTransitMode -> "Wake-Up Alert (Stops Before)"
                        isRoadMode -> "Alert Distance by Road"
                        else -> "Wake-Up Radius"
                    },
                    style = MaterialTheme.typography.labelLarge,
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

            // Audio Output & Vibration Options
            Text(
                text = "Audio & Alert Options",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioRouting.values().forEach { routing ->
                    val isSelected = (audioRouting == routing)
                    val (label, icon) = when (routing) {
                        AudioRouting.SPEAKER_ONLY -> "Speaker" to Icons.Default.VolumeUp
                        AudioRouting.BOTH -> "Both" to Icons.Default.NotificationsActive
                        AudioRouting.HEADPHONES_ONLY -> "Headphones" to Icons.Default.Headphones
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setAudioRouting(routing) },
                        leadingIcon = {
                            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            FilterChip(
                selected = vibrateEnabled,
                onClick = { viewModel.setVibrateEnabled(!vibrateEnabled) },
                label = {
                    Text(
                        text = if (vibrateEnabled) "Vibration On" else "Vibration Off",
                        fontWeight = if (vibrateEnabled) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (vibrateEnabled) Icons.Default.Vibration else Icons.AutoMirrored.Filled.VolumeMute,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Direct Road Trip Summary (Transit modes already show the 3-Stage breakdown at the top)
            if (!isTransitMode) {
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
                            Text(
                                text = if (isRoadMode) {
                                    "Reminder fires when $radiusLabel of road route remains to $targetName"
                                } else {
                                    "Alarm activates at $radiusLabel radius before $targetName"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                        it.ref?.contains(stationFilter, ignoreCase = true) == true ||
                        it.directionHint?.contains(stationFilter, ignoreCase = true) == true
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
                            text = if (isBusMode) "Auto — Nearest BMTC Stop" else "Auto — Nearest Metro by Road",
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
                                        val dirHint = stop.directionHint.orEmpty()
                                        if (dirHint.isNotBlank()) {
                                            Text(
                                                text = dirHint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stop.ref.orEmpty().split(',').take(3).joinToString(","),
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

@Composable
private fun ModeSelectorCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
