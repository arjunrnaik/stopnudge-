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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wakeme.app.alarm.WakeMeAlarmController
import com.wakeme.app.service.WakeMeTrackingService
import com.wakeme.app.util.LocationMarkerUtils
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.model.VehicleMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
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

    var isTestingAlert by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }
    var isHeadingUp by remember { mutableStateOf(true) }
    var showEndCommuteConfirmation by remember { mutableStateOf(false) }

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
                    val radiusLabel = if (radiusMeters >= 1000f) "${(radiusMeters / 1000f).toInt()}km" else "${radiusMeters.toInt()}m"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Alarm: $radiusLabel",
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
                            text = "END COMMUTE / STOP ALARM",
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

            // 1. TOP CARD: Destination, Distance & Trip Progress (from sketch: Destination, Distance, Trip Progress)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Destination Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = selectedVehicleMode.iconEmoji,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = targetName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Distance Countdown (Real road or approximate)
                    val dist = liveDistance ?: (straightDist?.let { it * 1.28f })
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (isRoadRoute) "ROAD DISTANCE REMAINING" else "ESTIMATED DISTANCE REMAINING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

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
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
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
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    }

                    // Live Telemetry Row (Speed & ETA)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚡ ${(currentSpeed ?: 0f).toInt()} km/h",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = estimatedEta?.let { "⏱️ ~$it min ETA" } ?: "⏱️ Approaching",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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

                                val myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                                    enableMyLocation()
                                    enableFollowLocation()
                                }
                                LocationMarkerUtils.applyModernLocationOverlay(ctx, myLocOverlay)
                                overlays.add(myLocOverlay)

                                val isBus = selectedVehicleMode == VehicleMode.BUS
                                val isMetro = selectedVehicleMode == VehicleMode.METRO
                                val busCat = BmtcBusCategory.fromRouteOrName(targetName)
                                val themeColorInt = when {
                                    isBus -> busCat.primaryColorInt
                                    isMetro -> NammaMetroNetwork.COLOR_PURPLE
                                    else -> 0xFF1E88E5.toInt()
                                }

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

                                val geofenceCircle = Polygon(this).apply {
                                    points = Polygon.pointsAsCircle(targetLocation, radiusMeters.toDouble())
                                    fillPaint.color = (0x33000000 or (themeColorInt and 0x00FFFFFF))
                                    outlinePaint.color = themeColorInt
                                    outlinePaint.strokeWidth = 3.5f
                                }
                                overlays.add(geofenceCircle)
                            }
                        },
                        update = { map ->
                            val currentPolys = map.overlays.filterIsInstance<Polyline>()
                            map.overlays.removeAll(currentPolys)

                            // Alternative routes in gray
                            for (alt in alternativeRoutes) {
                                val altPoly = Polyline(map).apply {
                                    setPoints(alt.points)
                                    outlinePaint.color = 0xFF90A4AE.toInt()
                                    outlinePaint.strokeWidth = 6f
                                    infoWindow = null
                                }
                                map.overlays.add(altPoly)
                            }

                            // Active route with authentic 50/50 two-tone styling for buses
                            if (routePoints.isNotEmpty()) {
                                if (selectedVehicleMode == VehicleMode.BUS) {
                                    val busCat = BmtcBusCategory.fromRouteOrName(targetName)
                                    val (baseWhite, dashedBlue) = LocationMarkerUtils.createTwoToneBusPolylines(
                                        mapView = map,
                                        points = routePoints,
                                        category = busCat,
                                        widthDp = 8.5f
                                    )
                                    map.overlays.add(baseWhite)
                                    map.overlays.add(dashedBlue)
                                } else {
                                    val lineColor = if (selectedVehicleMode == VehicleMode.METRO) NammaMetroNetwork.COLOR_PURPLE else 0xFF1976D2.toInt()
                                    val activePoly = Polyline(map).apply {
                                        setPoints(routePoints)
                                        outlinePaint.color = lineColor
                                        outlinePaint.strokeWidth = 9f
                                        infoWindow = null
                                    }
                                    map.overlays.add(activePoly)
                                }
                            }

                            if (isHeadingUp && currentBearing != null) {
                                map.mapOrientation = -currentBearing!!
                            } else {
                                map.mapOrientation = 0f
                            }

                            map.invalidate()
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isHeadingUp) "🧭 Heading" else "⬆️ North", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. BOTTOM CARD: Sleep Guard Active & Test Ring (from sketch: "Not sure" -> Sleep Guard)
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
                                text = "Sleep Guard Active 🛡️",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Alarm sounds at full volume even on Silent/DND mode.",
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
                                    delay(5000L) // 5 seconds test ring
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
                            imageVector = if (isTestingAlert) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTestingAlert) "Sounding Alert Test (5s)..." else "Test Ring 🔊",
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
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(16.5)

                            val myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                                enableMyLocation()
                                enableFollowLocation()
                            }
                            LocationMarkerUtils.applyModernLocationOverlay(ctx, myLocOverlay)
                            overlays.add(myLocOverlay)

                            val destMarker = Marker(this).apply {
                                position = targetLocation
                                title = targetName
                                icon = LocationMarkerUtils.createModernPinDrawable(ctx)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            overlays.add(destMarker)

                            val geofenceCircle = Polygon(this).apply {
                                points = Polygon.pointsAsCircle(targetLocation, radiusMeters.toDouble())
                                fillPaint.color = 0x331E88E5.toInt()
                                outlinePaint.color = 0xFF1E88E5.toInt()
                                outlinePaint.strokeWidth = 4f
                            }
                            overlays.add(geofenceCircle)
                        }
                    },
                    update = { map ->
                        val currentPolys = map.overlays.filterIsInstance<Polyline>()
                        map.overlays.removeAll(currentPolys)

                        if (routePoints.isNotEmpty()) {
                            if (selectedVehicleMode == VehicleMode.BUS) {
                                val busCat = BmtcBusCategory.fromRouteOrName(targetName)
                                val (baseWhite, dashedBlue) = LocationMarkerUtils.createTwoToneBusPolylines(
                                    mapView = map,
                                    points = routePoints,
                                    category = busCat,
                                    widthDp = 9.5f
                                )
                                map.overlays.add(baseWhite)
                                map.overlays.add(dashedBlue)
                            } else {
                                val lineColor = if (selectedVehicleMode == VehicleMode.METRO) NammaMetroNetwork.COLOR_PURPLE else 0xFF1976D2.toInt()
                                val activePoly = Polyline(map).apply {
                                    setPoints(routePoints)
                                    outlinePaint.color = lineColor
                                    outlinePaint.strokeWidth = 10f
                                    infoWindow = null
                                }
                                map.overlays.add(activePoly)
                            }
                        }

                        map.invalidate()
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
