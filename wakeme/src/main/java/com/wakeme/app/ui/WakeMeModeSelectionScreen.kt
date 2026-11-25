package com.wakeme.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wakeme.app.alarm.WakeMeAlarmController
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.model.VehicleMode
import com.wakeme.app.data.MultimodalLegType
import com.wakeme.app.data.MultimodalTripPlan
import com.wakeme.app.ui.MultimodalAlarmTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.util.LocationMarkerUtils
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

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
    val multimodalPlan by viewModel.multimodalPlan.collectAsState()
    val multimodalAlarmTarget by viewModel.multimodalAlarmTarget.collectAsState()

    var showCustomRadiusDialog by remember { mutableStateOf(false) }

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
                    val radiusLabel = if (radiusMeters >= 1000f) {
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
                        Text(
                            text = "${selectedVehicleMode.iconEmoji} START COMMUTE ALARM ($radiusLabel)",
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
                            Text(
                                text = String.format("%.4f° N, %.4f° E", targetLocation.latitude, targetLocation.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
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

                            val isBusMode = selectedVehicleMode == VehicleMode.BUS
                            val isMetroMode = selectedVehicleMode == VehicleMode.METRO
                            val isMultimodal = selectedVehicleMode == VehicleMode.MULTIMODAL
                            val themeColorInt = when {
                                isBusMode -> selectedBusCategory.primaryColorInt
                                isMetroMode -> NammaMetroNetwork.COLOR_PURPLE
                                isMultimodal -> 0xFF7B1FA2.toInt() // Deep Violet / Indigo
                                else -> 0xFF1E88E5.toInt()
                            }

                            val alarmTargetPoint = if (isMultimodal && multimodalAlarmTarget == MultimodalAlarmTarget.DEBOARDING_STATION && multimodalPlan != null && !multimodalPlan!!.isSameStationCatchment) {
                                multimodalPlan!!.exitStation.geoPoint
                            } else {
                                targetLocation
                            }

                            val destMarker = Marker(this).apply {
                                position = targetLocation
                                title = targetName
                                icon = when {
                                    isBusMode -> LocationMarkerUtils.createTwoToneBusBadgeDrawable(ctx, selectedBusCategory, sizeDp = 30)
                                    isMetroMode -> LocationMarkerUtils.createStationMarkerDrawable(ctx, targetName, NammaMetroNetwork.COLOR_PURPLE)
                                    isMultimodal -> LocationMarkerUtils.createStationMarkerDrawable(ctx, targetName, 0xFF7B1FA2.toInt())
                                    else -> LocationMarkerUtils.createModernPinDrawable(ctx)
                                }
                                setAnchor(Marker.ANCHOR_CENTER, if (isBusMode) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM)
                            }
                            overlays.add(destMarker)

                            // Exit Station Marker for Multimodal mode
                            if (isMultimodal && multimodalPlan != null && !multimodalPlan!!.isSameStationCatchment) {
                                val exitStation = multimodalPlan!!.exitStation
                                val exitMarker = Marker(this).apply {
                                    position = exitStation.geoPoint
                                    title = "Deboard: ${exitStation.name} Metro"
                                    icon = LocationMarkerUtils.createStationMarkerDrawable(ctx, exitStation.name, NammaMetroNetwork.COLOR_PURPLE)
                                    setAnchor(Marker.ANCHOR_CENTER, LocationMarkerUtils.getStationMarkerAnchorY(ctx))
                                }
                                overlays.add(exitMarker)
                            }

                            val geofenceCircle = Polygon(this).apply {
                                points = Polygon.pointsAsCircle(alarmTargetPoint, radiusMeters.toDouble())
                                fillPaint.color = (0x33000000 or (themeColorInt and 0x00FFFFFF))
                                outlinePaint.color = themeColorInt
                                outlinePaint.strokeWidth = 3.5f
                            }
                            overlays.add(geofenceCircle)
                        }
                    },
                    update = { map ->
                        val isMultimodal = selectedVehicleMode == VehicleMode.MULTIMODAL
                        val alarmTargetPoint = if (isMultimodal && multimodalAlarmTarget == MultimodalAlarmTarget.DEBOARDING_STATION && multimodalPlan != null && !multimodalPlan!!.isSameStationCatchment) {
                            multimodalPlan!!.exitStation.geoPoint
                        } else {
                            targetLocation
                        }
                        map.controller.setCenter(alarmTargetPoint)
                        val circle = map.overlays.filterIsInstance<Polygon>().firstOrNull()
                        if (circle != null) {
                            circle.points = Polygon.pointsAsCircle(alarmTargetPoint, radiusMeters.toDouble())
                            map.invalidate()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Travel Ways & Multimodal Mixed Transit (Google Maps Style)
            Text(
                text = "HOW DO YOU WANT TO TRAVEL?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Grid of 4 Ways to Travel:
            // Row 1: [ 🚕 Cab & Auto ] [ 🚇 Namma Metro ]
            // Row 2: [ 🔀 Mixed Mode (Google Maps) ] [ 🚌 City Bus (Future) ]
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Cab & Booking Transport
                    val isCab = selectedVehicleMode == VehicleMode.CAR
                    Surface(
                        onClick = { viewModel.setVehicleMode(VehicleMode.CAR) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCab) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isCab) 2.dp else 1.dp,
                            if (isCab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🚕", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Cab & Auto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Uber / Ola / Rapido", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 2: Namma Metro
                    val isMetro = selectedVehicleMode == VehicleMode.METRO
                    Surface(
                        onClick = { viewModel.setVehicleMode(VehicleMode.METRO) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMetro) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isMetro) 2.dp else 1.dp,
                            if (isMetro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🚇", fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Namma Metro", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Active Rapid Lines", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 3: Multimodal Mixed Transit (Google Maps Style: Walk + Metro + Cab/Bus)
                    val isMixed = selectedVehicleMode == VehicleMode.MULTIMODAL
                    Surface(
                        onClick = { viewModel.setVehicleMode(VehicleMode.MULTIMODAL) },
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMixed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (isMixed) 2.dp else 1.dp,
                            if (isMixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔀", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Google Maps",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Mixed Transit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Walk + Metro + Cab", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 4: City Bus (BMTC - In Future)
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
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚌", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFF9800).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "In Future",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("City Bus", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("BMTC Details", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 3b. Multimodal Mixed Transit Details Card (Google Maps Style)
            AnimatedVisibility(visible = selectedVehicleMode == VehicleMode.MULTIMODAL) {
                val plan = multimodalPlan
                if (plan != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header: Summary Chips & Total Time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔀 JOURNEY BREAKDOWN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "⏱️ ~${plan.totalDurationMinutes} mins total",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Flow chip: e.g. 🚶 6m ➔ 🚇 22m ➔ 🚶 4m
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = plan.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            // Step-by-Step Multimodal Legs (Google Maps Style)
                            plan.legs.forEachIndexed { idx, leg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = when (leg.type) {
                                            MultimodalLegType.WALK -> Color(0xFF4CAF50)
                                            MultimodalLegType.METRO -> Color(NammaMetroNetwork.COLOR_PURPLE)
                                            MultimodalLegType.CAB -> Color(0xFF1976D2)
                                            MultimodalLegType.BUS -> Color(0xFF00838F)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(leg.type.iconEmoji, fontSize = 14.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = leg.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = leg.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)

                            // Multimodal Alarm Target Switcher
                            Text(
                                text = "COMMUTE ALARM WAKE-UP TARGET:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Option A: Metro Deboarding Station
                            val isDeboardTarget = multimodalAlarmTarget == MultimodalAlarmTarget.DEBOARDING_STATION
                            Surface(
                                onClick = { viewModel.setMultimodalAlarmTarget(MultimodalAlarmTarget.DEBOARDING_STATION) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDeboardTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (isDeboardTarget) 1.5.dp else 1.dp,
                                    if (isDeboardTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isDeboardTarget,
                                        onClick = { viewModel.setMultimodalAlarmTarget(MultimodalAlarmTarget.DEBOARDING_STATION) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "🚇 Wake me before Deboarding Metro (${plan.exitStation.name})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Alarm sounds 800m before your exit station so you never miss your stop.",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Option B: Final Destination
                            val isFinalTarget = multimodalAlarmTarget == MultimodalAlarmTarget.FINAL_DESTINATION
                            Surface(
                                onClick = { viewModel.setMultimodalAlarmTarget(MultimodalAlarmTarget.FINAL_DESTINATION) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isFinalTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (isFinalTarget) 1.5.dp else 1.dp,
                                    if (isFinalTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isFinalTarget,
                                        onClick = { viewModel.setMultimodalAlarmTarget(MultimodalAlarmTarget.FINAL_DESTINATION) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "📍 Wake me before Final Destination ($targetName)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Alarm triggers when arriving at your final destination.",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "💡 BMTC feeder bus schedules will be integrated into multimodal routes in an upcoming release.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // 3c. City Bus (In Future Update Notice)
            AnimatedVisibility(visible = selectedVehicleMode == VehicleMode.BUS) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚌", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BMTC City Bus Integration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                        Text(
                            text = "We are refining the BMTC bus network dataset and will add live bus stops, routes, and timings in an upcoming update.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "👉 For daily commuting right now, please use Namma Metro or Mixed Transit (Walk + Metro + Cab).",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 4. Wake-Up Radius Distance (from sketch: [200] [500] [1k] [2k] + [custom values])
            Text(
                text = "WAKE-UP RADIUS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

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
                    Column {
                        Text(
                            text = "Trip Summary",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Alarm activates at $radiusLabel perimeter before $targetName",
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
                Text("Set Custom Wake-Up Radius", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter distance in meters (50m to 10,000m):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Radius (meters)") },
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
}
