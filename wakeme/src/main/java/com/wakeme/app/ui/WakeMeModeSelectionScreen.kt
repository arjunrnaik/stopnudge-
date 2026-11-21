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
                                outlinePaint.strokeWidth = 3f
                            }
                            overlays.add(geofenceCircle)
                        }
                    },
                    update = { map ->
                        map.controller.setCenter(targetLocation)
                        val circle = map.overlays.filterIsInstance<Polygon>().firstOrNull()
                        if (circle != null) {
                            circle.points = Polygon.pointsAsCircle(targetLocation, radiusMeters.toDouble())
                            map.invalidate()
                        }
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
                // Bus Button
                val isBus = selectedVehicleMode == VehicleMode.BUS
                Surface(
                    onClick = { viewModel.setVehicleMode(VehicleMode.BUS) },
                    modifier = Modifier.weight(1f),
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
                        Text("BMTC / KSRTC", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Metro Button
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
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚇", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Namma Metro", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Line Transfer", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Direct Road Button (sketch: Direction)
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
