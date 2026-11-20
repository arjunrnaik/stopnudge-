package com.wakeme.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.wakeme.app.model.DestinationConfig
import com.wakeme.app.model.VehicleMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Screen 2: Mode Setup & Alarm Configuration Screen.
 * Implements the user's approved notebook sketch design:
 * - Top Route Card: Origin (Your location) -> Destination.
 * - Route Mini-Map Preview with destination pin and dynamic geofence radius circle.
 * - Mode Selector: [City Bus] [Namma Metro] [Direct Road / Auto].
 * - Wake-up Radius: [ 200m ] [ 500m ] [ 1 km ] [ 2 km ] + [ Custom... ]
 * - Audio & Vibration options: Speaker, Speaker + Headphones, Headphones, and Vibrate (+/-).
 * - Deboard reminder note & big [ START COMMUTE ALARM ] CTA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeMeModeSelectionScreen(
    viewModel: WakeMeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val targetName by viewModel.targetName.collectAsState()
    val targetLocation by viewModel.targetLocation.collectAsState()
    val selectedVehicleMode by viewModel.selectedVehicleMode.collectAsState()
    val radiusMeters by viewModel.radiusMeters.collectAsState()
    val audioRouting by viewModel.audioRouting.collectAsState()
    val vibrateEnabled by viewModel.vibrateEnabled.collectAsState()
    val reminderNote by viewModel.reminderNote.collectAsState()

    var isPreviewingMode by remember { mutableStateOf<VehicleMode?>(null) }
    var showCustomRadiusDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Commute & Alarm Setup",
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
                            contentDescription = "Back to Map"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Big Bottom CTA Button
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
                            text = "${selectedVehicleMode.iconEmoji} Start Alarm ($radiusLabel)",
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
            // 1. Top Route Connection Card (from sketch: • your location | • destination)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Origin row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32)) // Green dot for origin
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Your Location",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Current GPS Position",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Route connector line
                    Box(
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .width(2.dp)
                            .height(18.dp)
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Destination",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

            // 2. Compact Route Map Preview with dynamic geofence radius circle (from sketch: + Route MAP)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
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

            // 3. Commute Vehicle Mode Selection (from sketch: [Bus] [Metro] [Direction])
            Text(
                text = "COMMUTE VEHICLE MODE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            VehicleMode.values().forEach { mode ->
                val isSelected = (selectedVehicleMode == mode)
                val isPreviewing = (isPreviewingMode == mode)
                VehicleCard(
                    mode = mode,
                    isSelected = isSelected,
                    isPreviewing = isPreviewing,
                    onClick = { viewModel.setVehicleMode(mode) },
                    onPreviewSound = {
                        if (isPreviewingMode == null) {
                            isPreviewingMode = mode
                            val previewConfig = DestinationConfig(
                                name = "Sound Preview",
                                latitude = 0.0,
                                longitude = 0.0,
                                vehicleMode = mode,
                                alertMode = mode.alertMode,
                                audioRouting = audioRouting,
                                vibrate = vibrateEnabled
                            )
                            WakeMeAlarmController.triggerAlert(context, previewConfig)
                            coroutineScope.launch {
                                delay(2200L)
                                WakeMeAlarmController.stopAlert(context)
                                isPreviewingMode = null
                            }
                        } else {
                            WakeMeAlarmController.stopAlert(context)
                            isPreviewingMode = null
                        }
                    }
                )
            }

            // 4. Wake-Up Radius Distance (from sketch: [200] [500] [1k] [2k] + [custom values])
            Text(
                text = "WAKE-UP RADIUS DISTANCE",
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
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
                        Text(
                            text = customLabel,
                            fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
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

            // Audio Routing Chips: Speaker Only, Speaker + Headphones, Headphones Only
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
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Vibration Toggle (+ / -)
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

            // 6. Deboard Reminder Note (Optional)
            Text(
                text = "DEBOARD NOTE (OPTIONAL)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = reminderNote,
                onValueChange = { viewModel.setReminderNote(it) },
                placeholder = {
                    Text(
                        "e.g. Take backpack, change to Green Line",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
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

@Composable
private fun VehicleCard(
    mode: VehicleMode,
    isSelected: Boolean,
    isPreviewing: Boolean,
    onClick: () -> Unit,
    onPreviewSound: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mode.iconEmoji,
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = mode.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = mode.alertTag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = when (mode) {
                                    VehicleMode.BUS -> MaterialTheme.colorScheme.errorContainer
                                    VehicleMode.METRO -> MaterialTheme.colorScheme.primaryContainer
                                    VehicleMode.CAR -> MaterialTheme.colorScheme.secondaryContainer
                                }
                            ),
                            border = null
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sound Preview Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onPreviewSound,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPreviewing) "Playing..." else "Preview Sound",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
