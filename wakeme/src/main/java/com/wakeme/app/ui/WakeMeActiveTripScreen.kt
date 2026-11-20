package com.wakeme.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.wakeme.app.data.RouteCandidate
import com.wakeme.app.model.AlertMode
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.service.WakeMeTrackingService
import com.wakeme.app.util.LocationMarkerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Screen 3: Active Live Ride Tracking HUD.
 * Real-time satellite GPS countdown with accurate Road Driving Distance (OSRM),
 * moving navigation arrow map with live route polyline in an expandable preview box,
 * speed & ETA telemetry, snooze alert controls, and stop tracking button.
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
    val alertMode by viewModel.alertMode.collectAsState()
    val audioRouting by viewModel.audioRouting.collectAsState()
    val reminderNote by viewModel.reminderNote.collectAsState()

    val liveDistance by WakeMeTrackingService.liveDistanceMeters.collectAsState()
    val straightDist by WakeMeTrackingService.straightLineDistanceMeters.collectAsState()
    val currentSpeed by WakeMeTrackingService.currentSpeedKmh.collectAsState()
    val estimatedEta by WakeMeTrackingService.estimatedEtaMinutes.collectAsState()
    val initialDist by WakeMeTrackingService.initialDistanceMeters.collectAsState()
    val isSnoozed by WakeMeAlarmController.isSnoozed.collectAsState()
    val routePoints by WakeMeTrackingService.routePolyline.collectAsState()
    val alternativeRoutes by WakeMeTrackingService.alternativeRoutes.collectAsState()
    val allCandidateRoutes by WakeMeTrackingService.allCandidateRoutes.collectAsState()
    val selectedRouteId by WakeMeTrackingService.selectedRouteId.collectAsState()
    val isRerouting by WakeMeTrackingService.isRerouting.collectAsState()
    val isRoadRoute by WakeMeTrackingService.isRoadRouteActive.collectAsState()
    val currentBearing by WakeMeTrackingService.currentBearing.collectAsState()

    var isTestingAlert by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }
    var isHeadingUp by remember { mutableStateOf(true) }
    var showEndCommuteConfirmation by remember { mutableStateOf(false) }

    // Pulsing animation for the satellite indicator
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

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
                    Text(
                        text = "Live Ride Tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Stop Tracking Bottom CTA
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
            // 1. Hardware GNSS / Satellite Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B5E20).copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32).copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "🛰️ HARDWARE GNSS ACTIVE — 0 DATA CONSUMED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            text = "Tracking operates offline via direct GPS satellites.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // Snooze Banner (if commuter snoozed the alarm)
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

            // 2. Giant Live Distance Hero Card (Real Road Distance)
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
                        .padding(vertical = 24.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = selectedVehicleMode.iconEmoji,
                            fontSize = 28.sp
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

                    Spacer(modifier = Modifier.height(16.dp))

                    val dist = liveDistance ?: (straightDist?.let { it * 1.28f })
                    val distanceText = if (dist != null) {
                        if (dist >= 1000f) {
                            String.format("%.1f km", dist / 1000f)
                        } else {
                            "${dist.toInt()} m"
                        }
                    } else {
                        "Searching..."
                    }

                    Text(
                        text = distanceText,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isRoadRoute) "ROAD DRIVING DISTANCE" else "APPROX. ROAD DISTANCE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real-time Journey Progress Bar
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

                    Spacer(modifier = Modifier.height(14.dp))

                    val triggerText = if (radiusMeters >= 1000f) {
                        "${(radiusMeters / 1000f).toInt()} km"
                    } else {
                        "${radiusMeters.toInt()}m"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = "Alarm triggers at $triggerText perimeter",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 3. Live Telemetry HUD: Speed & ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Speed Chip
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CURRENT SPEED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚡ ${(currentSpeed ?: 0f).toInt()} km/h",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ETA Chip
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ESTIMATED ARRIVAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = estimatedEta?.let { "⏱️ ~$it min" } ?: "⏱️ Near",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Google Maps-Style "Recalculating..." Banner
            AnimatedVisibility(
                visible = isRerouting,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Recalculating earliest route...",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 4. Moving Navigation Map Box (Expandable)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Embedded Map with Directional Arrow & Follow Mode
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

                                val destMarker = Marker(this).apply {
                                    position = targetLocation
                                    title = targetName
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                overlays.add(destMarker)

                                val geofenceCircle = Polygon(this).apply {
                                    points = Polygon.pointsAsCircle(targetLocation, radiusMeters.toDouble())
                                    fillPaint.color = 0x331E88E5.toInt()
                                    outlinePaint.color = 0xFF1E88E5.toInt()
                                    outlinePaint.strokeWidth = 3f
                                }
                                overlays.add(geofenceCircle)
                            }
                        },
                        update = { map ->
                            // Remove existing route polylines
                            val currentPolys = map.overlays.filterIsInstance<Polyline>()
                            map.overlays.removeAll(currentPolys)

                            // 1. Draw alternative routes underneath in muted slate gray
                            for (alt in alternativeRoutes) {
                                val altPoly = Polyline(map).apply {
                                    setPoints(alt.points)
                                    outlinePaint.color = 0xFF90A4AE.toInt()
                                    outlinePaint.strokeWidth = 7f
                                    infoWindow = null
                                    setOnClickListener { _, _, _ ->
                                        WakeMeTrackingService.selectRoute(alt.id)
                                        true
                                    }
                                }
                                map.overlays.add(altPoly)
                            }

                            // 2. Draw fastest / active route on top in vibrant blue
                            if (routePoints.isNotEmpty()) {
                                val activePoly = Polyline(map).apply {
                                    setPoints(routePoints)
                                    outlinePaint.color = 0xFF1976D2.toInt()
                                    outlinePaint.strokeWidth = 10f
                                    infoWindow = null
                                    setOnClickListener { _, _, _ -> true }
                                }
                                map.overlays.add(activePoly)
                            }

                            // 3. Dynamic Heading-Up Rotation: Map turns with vehicle
                            if (isHeadingUp && currentBearing != null) {
                                map.mapOrientation = -currentBearing!!
                            } else {
                                map.mapOrientation = 0f
                            }

                            map.invalidate()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Compass Mode Toggle FAB (Heading-Up vs North-Up)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isHeadingUp = !isHeadingUp }
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isHeadingUp) "🧭" else "⬆️", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHeadingUp) "Heading" else "North",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Header Bar on Mini-Map
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🧭 Moving Arrow View",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isRoadRoute) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Road Route",
                                    color = Color(0xFF81C784),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isMapExpanded = true },
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Expand Map",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Expand",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Alternative Routes Selection Chips
            if (allCandidateRoutes.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROUTES:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    allCandidateRoutes.forEach { route ->
                        val isSelected = route.id == selectedRouteId
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                WakeMeTrackingService.selectRoute(route.id)
                            },
                            label = { Text(route.summary) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            // 5. Deboard Reminder Note (if set)
            if (reminderNote.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    border = null
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.StickyNote2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "DEBOARD REMINDER NOTE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = reminderNote,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // 6. Trip Setup Details
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Commute Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${selectedVehicleMode.iconEmoji} ${selectedVehicleMode.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Alert Type",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (alertMode == AlertMode.WAKEUP_ALARM) "⏰ Loud Wake-up Siren" else "🔔 Gentle Reminder",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Audio Routing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (audioRouting) {
                                AudioRouting.HEADPHONES_ONLY -> "🎧 Headphones Only"
                                AudioRouting.SPEAKER_ONLY -> "🔊 Phone Speaker"
                                AudioRouting.BOTH -> "🔔 Headphones + Speaker"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 7. Sleep Guard Active & Test Ring Card (from sketch: "Not sure" -> Sleep Guard)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                                text = "Alarm sounds at full volume even if phone is on Silent or DND.",
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
                                    delay(5000L) // Sound test for 5 seconds
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

            Spacer(modifier = Modifier.height(24.dp))
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
                    text = "Are you sure you want to stop tracking and cancel the destination alarm?",
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

    // Full-Screen Interactive Map Modal Dialog
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
                        // Remove existing route polylines
                        val currentPolys = map.overlays.filterIsInstance<Polyline>()
                        map.overlays.removeAll(currentPolys)

                        // 1. Draw alternative routes underneath in muted slate gray
                        for (alt in alternativeRoutes) {
                            val altPoly = Polyline(map).apply {
                                setPoints(alt.points)
                                outlinePaint.color = 0xFF90A4AE.toInt()
                                outlinePaint.strokeWidth = 7f
                                infoWindow = null
                                setOnClickListener { _, _, _ ->
                                    WakeMeTrackingService.selectRoute(alt.id)
                                    true
                                }
                            }
                            map.overlays.add(altPoly)
                        }

                        // 2. Draw fastest / active route on top in vibrant blue
                        if (routePoints.isNotEmpty()) {
                            val activePoly = Polyline(map).apply {
                                setPoints(routePoints)
                                outlinePaint.color = 0xFF1976D2.toInt()
                                outlinePaint.strokeWidth = 11f
                                infoWindow = null
                                setOnClickListener { _, _, _ -> true }
                            }
                            map.overlays.add(activePoly)
                        }

                        // 3. Dynamic Heading-Up Rotation
                        if (isHeadingUp && currentBearing != null) {
                            map.mapOrientation = -currentBearing!!
                        } else {
                            map.mapOrientation = 0f
                        }

                        map.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Controls: Compass Toggle (Left) & Close Button (Right)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { isHeadingUp = !isHeadingUp }
                            .shadow(4.dp, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isHeadingUp) "🧭" else "⬆️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHeadingUp) "Heading-Up (Auto)" else "North-Up (Locked)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = { isMapExpanded = false },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Expanded Map",
                            tint = Color.White
                        )
                    }
                }

                // Bottom Floating Telemetry Overlay & Route Switcher
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (allCandidateRoutes.size > 1) {
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ROUTES:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            allCandidateRoutes.forEach { route ->
                                val isSelected = route.id == selectedRouteId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        WakeMeTrackingService.selectRoute(route.id)
                                    },
                                    label = { Text(route.summary) },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val dist = liveDistance ?: (straightDist?.let { it * 1.28f })
                                Text(
                                    text = if (dist != null) {
                                        if (dist >= 1000f) String.format("%.1f km", dist / 1000f) else "${dist.toInt()}m"
                                    } else "...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Remaining", style = MaterialTheme.typography.labelSmall)
                            }
                            VerticalDivider(modifier = Modifier.height(28.dp))
                            Column {
                                Text(
                                    text = "⚡ ${(currentSpeed ?: 0f).toInt()} km/h",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Speed", style = MaterialTheme.typography.labelSmall)
                            }
                            VerticalDivider(modifier = Modifier.height(28.dp))
                            Column {
                                Text(
                                    text = estimatedEta?.let { "~$it min" } ?: "Near",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("ETA", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
