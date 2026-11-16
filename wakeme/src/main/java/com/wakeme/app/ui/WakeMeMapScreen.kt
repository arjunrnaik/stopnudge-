package com.wakeme.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wakeme.app.data.SearchResultItem
import com.wakeme.app.data.SearchResultType
import com.wakeme.app.data.local.favorites.FavoritePlaceEntity
import com.wakeme.app.model.AlertMode
import com.wakeme.app.model.AudioRouting
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeMeMapScreen(
    viewModel: WakeMeViewModel,
    onStartTracking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val targetLocation by viewModel.targetLocation.collectAsState()
    val targetName by viewModel.targetName.collectAsState()
    val radiusMeters by viewModel.radiusMeters.collectAsState()
    val alertMode by viewModel.alertMode.collectAsState()
    val audioRouting by viewModel.audioRouting.collectAsState()
    val reminderNote by viewModel.reminderNote.collectAsState()
    val showTransitOverlay by viewModel.showTransitOverlay.collectAsState()
    val metroStations by viewModel.metroStations.collectAsState()
    val isTrackingActive by viewModel.isTrackingActive.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var isMapMoving by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var geofencePolygonRef by remember { mutableStateOf<Polygon?>(null) }
    var transitMarkersList by remember { mutableStateOf<List<Marker>>(emptyList()) }

    var debounceJob by remember { mutableStateOf<Job?>(null) }
    var showSaveFavoriteDialog by remember { mutableStateOf(false) }
    var favoriteCustomName by remember { mutableStateOf("") }

    // Observe camera move target events (e.g. from search or Google Maps intent)
    LaunchedEffect(viewModel) {
        viewModel.cameraMoveTarget.collect { target ->
            mapViewRef?.controller?.animateTo(target)
        }
    }

    // Lifecycle observer for MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
        }
    }

    // Update Geofence Circle Overlay whenever targetLocation or radiusMeters changes
    LaunchedEffect(targetLocation, radiusMeters, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        val currentPolygon = geofencePolygonRef ?: Polygon(map).also { poly ->
            poly.fillPaint.color = android.graphics.Color.argb(45, 79, 70, 229)
            poly.outlinePaint.color = android.graphics.Color.argb(220, 79, 70, 229)
            poly.outlinePaint.strokeWidth = 5f
            map.overlays.add(0, poly)
            geofencePolygonRef = poly
        }
        val circlePoints = Polygon.pointsAsCircle(targetLocation, radiusMeters.toDouble())
        currentPolygon.points = circlePoints
        map.invalidate()
    }

    // Toggle Transit Markers overlay
    LaunchedEffect(showTransitOverlay, metroStations, mapViewRef) {
        val map = mapViewRef ?: return@LaunchedEffect
        // Remove previous transit markers
        transitMarkersList.forEach { map.overlays.remove(it) }
        transitMarkersList = emptyList()

        if (showTransitOverlay && metroStations.isNotEmpty()) {
            val newMarkers = metroStations.map { station ->
                Marker(map).apply {
                    position = GeoPoint(station.latitude, station.longitude)
                    title = station.name
                    snippet = "Metro • ${station.metroLines}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { marker, _ ->
                        marker.showInfoWindow()
                        viewModel.updateTargetLocation(marker.position, marker.title)
                        true
                    }
                }
            }
            map.overlays.addAll(newMarkers)
            transitMarkersList = newMarkers
            map.invalidate()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Full-screen OsmDroid Map
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(viewModel.defaultCenter)

                    val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    myLocationOverlay.enableMyLocation()
                    overlays.add(myLocationOverlay)
                    locationOverlayRef = myLocationOverlay

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            isMapMoving = true
                            debounceJob?.cancel()
                            debounceJob = coroutineScope.launch {
                                delay(220)
                                isMapMoving = false
                                val center = mapCenter as? GeoPoint ?: GeoPoint(mapCenter.latitude, mapCenter.longitude)
                                viewModel.updateTargetLocation(center)
                            }
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            return false
                        }
                    })

                    mapViewRef = this
                }
            }
        )

        // 2. Center-Screen Floating Pin (Uber/Swiggy Style)
        val pinLift by animateDpAsState(
            targetValue = if (isMapMoving) (-22).dp else (-10).dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "PinHover"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Drop Shadow on the ground (exact map coordinate point)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (isMapMoving) 0.25f else 0.5f))
            )

            // Hovering Pin Icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = pinLift)
            ) {
                // Floating status badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = if (isMapMoving) "Release to set destination" else targetName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Vibrant Pin
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Target Pin",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // 3. Floating Quick Action Controls (Top End)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 160.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Recenter GPS Button
            SmallFloatingActionButton(
                onClick = {
                    locationOverlayRef?.myLocation?.let { myLoc ->
                        mapViewRef?.controller?.animateTo(myLoc)
                        viewModel.updateTargetLocation(myLoc, "Current GPS Location")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Current Location")
            }

            // Transit Stops Toggle Button
            SmallFloatingActionButton(
                onClick = { viewModel.toggleTransitOverlay() },
                containerColor = if (showTransitOverlay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (showTransitOverlay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.DirectionsTransit, contentDescription = "Toggle Transit Layer")
            }

            // Zoom In (+)
            SmallFloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomIn() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out (-)
            SmallFloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomOut() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }

        // 4. Top Unified Search Bar, Results Dropdown & 1-Tap Favorites Carousel
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 44.dp)
        ) {
            WakeMeSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onClear = {
                    viewModel.clearSearch()
                    focusManager.clearFocus()
                },
                isSearching = isSearching
            )

            // When searching: show dropdown results
            AnimatedVisibility(
                visible = searchResults.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                SearchResultsDropdown(
                    results = searchResults,
                    onSelect = { item ->
                        viewModel.onSearchResultSelected(item)
                        focusManager.clearFocus()
                    }
                )
            }

            // When NOT searching: show 1-Tap Quick Favorites Carousel
            if (searchResults.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                QuickFavoritesCarousel(
                    favorites = favorites,
                    onSelect = { viewModel.selectFavorite(it) },
                    onAddCurrent = {
                        favoriteCustomName = targetName
                        showSaveFavoriteDialog = true
                    }
                )
            }
        }

        // 5. Bottom Configuration Card (Destination, Radius, Alert Modes & Notes)
        WakeMeBottomDestinationCard(
            modifier = Modifier.align(Alignment.BottomCenter),
            targetName = targetName,
            targetLocation = targetLocation,
            radiusMeters = radiusMeters,
            onRadiusChange = { viewModel.setRadius(it) },
            alertMode = alertMode,
            onAlertModeChange = { viewModel.setAlertMode(it) },
            audioRouting = audioRouting,
            onAudioRoutingChange = { viewModel.setAudioRouting(it) },
            reminderNote = reminderNote,
            onReminderNoteChange = { viewModel.setReminderNote(it) },
            isTrackingActive = isTrackingActive,
            onSaveAsFavorite = {
                favoriteCustomName = targetName
                showSaveFavoriteDialog = true
            },
            onConfirm = {
                viewModel.setTrackingActive(!isTrackingActive)
                onStartTracking()
            }
        )

        // Save Favorite Dialog
        if (showSaveFavoriteDialog) {
            AlertDialog(
                onDismissRequest = { showSaveFavoriteDialog = false },
                title = { Text("Save Favorite Place") },
                text = {
                    Column {
                        Text(
                            text = "Save this place for 1-tap alarms anytime:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = favoriteCustomName,
                            onValueChange = { favoriteCustomName = it },
                            label = { Text("Place Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveCurrentAsFavorite(favoriteCustomName)
                            showSaveFavoriteDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveFavoriteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun QuickFavoritesCarousel(
    favorites: List<FavoritePlaceEntity>,
    onSelect: (FavoritePlaceEntity) -> Unit,
    onAddCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick + Add Button
        AssistChip(
            onClick = onAddCurrent,
            leadingIcon = {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Favorite", modifier = Modifier.size(16.dp))
            },
            label = { Text("Save Pin") },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            )
        )

        // Saved favorites
        favorites.forEach { fav ->
            val icon = when (fav.category.uppercase()) {
                "HOME" -> Icons.Default.Home
                "WORK" -> Icons.Default.Work
                "TRANSIT" -> Icons.Default.DirectionsTransit
                else -> Icons.Default.Star
            }
            SuggestionChip(
                onClick = { onSelect(fav) },
                icon = {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        text = fav.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }
    }
}

@Composable
fun WakeMeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(10.dp))

            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Search stop, metro, or place...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultsDropdown(
    results: List<SearchResultItem>,
    onSelect: (SearchResultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(results) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (item.type) {
                        SearchResultType.METRO -> Icons.Default.DirectionsTransit
                        SearchResultType.BUS -> Icons.Default.DirectionsBus
                        SearchResultType.ONLINE_PLACE -> Icons.Default.Place
                    }
                    val iconColor = when (item.type) {
                        SearchResultType.METRO -> MaterialTheme.colorScheme.primary
                        SearchResultType.BUS -> MaterialTheme.colorScheme.secondary
                        SearchResultType.ONLINE_PLACE -> MaterialTheme.colorScheme.tertiary
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun WakeMeBottomDestinationCard(
    targetName: String,
    targetLocation: GeoPoint,
    radiusMeters: Float,
    onRadiusChange: (Float) -> Unit,
    alertMode: AlertMode,
    onAlertModeChange: (AlertMode) -> Unit,
    audioRouting: AudioRouting,
    onAudioRoutingChange: (AudioRouting) -> Unit,
    reminderNote: String,
    onReminderNoteChange: (String) -> Unit,
    isTrackingActive: Boolean,
    onSaveAsFavorite: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Destination header with Save Favorite Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = targetName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format("%.4f° N, %.4f° E", targetLocation.latitude, targetLocation.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onSaveAsFavorite) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Save as Favorite",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Geofence Radius Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Alarm Radius",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${radiusMeters.toInt()} meters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Quick Radius Preset Chips
            val presets = listOf(100f, 250f, 500f, 1000f, 2000f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = (radiusMeters == preset)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onRadiusChange(preset) },
                        label = {
                            Text(if (preset >= 1000f) "${(preset / 1000).toInt()} km" else "${preset.toInt()} m")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Alert Mode & Audio Routing Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Alert Mode
                AlertMode.values().forEach { mode ->
                    val isSelected = (alertMode == mode)
                    OutlinedButton(
                        onClick = { onAlertModeChange(mode) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.takeIf { !isSelected }
                    ) {
                        Icon(
                            imageVector = if (mode == AlertMode.WAKEUP_ALARM) Icons.Default.Alarm else Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (mode == AlertMode.WAKEUP_ALARM) "Alarm" else "Reminder",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Optional Reminder Note Text Field
            OutlinedTextField(
                value = reminderNote,
                onValueChange = onReminderNoteChange,
                placeholder = {
                    Text(
                        "Optional note (e.g. Collect bag, change platform)",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Start/Stop Tracking Button
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTrackingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (isTrackingActive) Icons.Default.Stop else Icons.Default.Navigation,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTrackingActive) "Stop Alarm & Tracking" else "Set Alarm Here (${radiusMeters.toInt()}m)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
