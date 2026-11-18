package com.wakeme.app.ui

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
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
import com.wakeme.app.util.LocationMarkerUtils
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
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Screen 1: Uber/Rapido-Style Search Landing Screen.
 * Full-screen map background with modern medium-sized blue GPS dot (no "yellow man"),
 * clean view with NO permanent center pin, tap-anywhere-on-map to drop & adjust pin,
 * prominent "Where are you heading?" search bar, 1-tap favorites carousel, and transit shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeMeLandingScreen(
    viewModel: WakeMeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val targetLocation by viewModel.targetLocation.collectAsState()
    val showTransitOverlay by viewModel.showTransitOverlay.collectAsState()
    val metroStations by viewModel.metroStations.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recentTrips by viewModel.recentTrips.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var currentMapCenter by remember { mutableStateOf(targetLocation) }
    var transitMarkersList by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Tap-to-drop pin state (replaces permanent center pin)
    var droppedPinLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var droppedPinMarker by remember { mutableStateOf<Marker?>(null) }

    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var editingPlaceEntity by remember { mutableStateOf<FavoritePlaceEntity?>(null) }

    // Sync camera animations when target changes from search or favorites
    LaunchedEffect(viewModel) {
        viewModel.cameraMoveTarget.collect { target ->
            mapViewRef?.controller?.animateTo(target)
        }
    }

    // Update transit markers when overlay toggled
    LaunchedEffect(showTransitOverlay, metroStations) {
        val map = mapViewRef ?: return@LaunchedEffect
        transitMarkersList.forEach { map.overlays.remove(it) }
        transitMarkersList = emptyList()

        if (showTransitOverlay) {
            val newMarkers = mutableListOf<Marker>()
            metroStations.forEach { metro ->
                val marker = Marker(map).apply {
                    position = GeoPoint(metro.latitude, metro.longitude)
                    title = metro.name
                    snippet = "Metro (${metro.metroLines})"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        viewModel.selectMapPin(
                            GeoPoint(metro.latitude, metro.longitude),
                            "${metro.name} Metro Station"
                        )
                        true
                    }
                }
                map.overlays.add(marker)
                newMarkers.add(marker)
            }
            transitMarkersList = newMarkers
            map.invalidate()
        } else {
            map.invalidate()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Full-Screen Interactive OsmDroid Map
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(targetLocation)

                    // Modern medium blue GPS dot & navigation arrow (replaces the yellow person icon)
                    val locOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation()
                        enableFollowLocation()
                    }
                    LocationMarkerUtils.applyModernLocationOverlay(ctx, locOverlay)
                    overlays.add(locOverlay)
                    locationOverlayRef = locOverlay

                    // Tap on Map to drop / move pin overlay
                    val tapOverlay = object : Overlay() {
                        override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                            val geo = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as? GeoPoint
                            if (geo != null) {
                                droppedPinLocation = geo

                                if (droppedPinMarker == null) {
                                    val marker = Marker(mapView).apply {
                                        position = geo
                                        title = "Selected Location"
                                        snippet = "Tap anywhere to move pin"
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    }
                                    mapView.overlays.add(marker)
                                    droppedPinMarker = marker
                                } else {
                                    droppedPinMarker?.position = geo
                                }
                                mapView.invalidate()
                                return true
                            }
                            return false
                        }
                    }
                    overlays.add(tapOverlay)

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            debounceJob?.cancel()
                            debounceJob = coroutineScope.launch {
                                delay(200)
                                val center = mapCenter as? GeoPoint ?: return@launch
                                currentMapCenter = center
                            }
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean = true
                    })

                    mapViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Map Lifecycle Management
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        mapViewRef?.onResume()
                        locationOverlayRef?.enableMyLocation()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        locationOverlayRef?.disableMyLocation()
                        mapViewRef?.onPause()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapViewRef?.onDetach()
            }
        }

        // 2. Top Header: Search Bar & 1-Tap Quick Favorites Carousel
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // "Where are you heading?" Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = {
                            Text(
                                "Where are you heading?",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.weight(1f)
                    )
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Autocomplete Results Dropdown
            AnimatedVisibility(
                visible = searchResults.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .shadow(10.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(searchResults) { result ->
                            SearchResultRow(
                                item = result,
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.onSearchResultSelected(result)
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }


        }

        // 3. Right-side Floating Controls (Transit Layer + My Location)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Transit Overlay Toggle
            FloatingActionButton(
                onClick = { viewModel.toggleTransitOverlay() },
                containerColor = if (showTransitOverlay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (showTransitOverlay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsTransit,
                    contentDescription = "Toggle Transit Overlay",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Re-center on GPS Location
            FloatingActionButton(
                onClick = {
                    val myLoc = locationOverlayRef?.myLocation
                    if (myLoc != null) {
                        mapViewRef?.controller?.animateTo(myLoc)
                        currentMapCenter = myLoc
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 4. Bottom Section: Bottom Sheet with Dropped Pin Action, Saved Places & Commute History
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .align(Alignment.CenterHorizontally)
                )

                // Dropped Pin Action Card (if user tapped map)
                val pin = droppedPinLocation
                if (pin != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Selected Map Location",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format("%.4f° N, %.4f° E", pin.latitude, pin.longitude),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        droppedPinMarker?.let { mapViewRef?.overlays?.remove(it) }
                                        droppedPinMarker = null
                                        droppedPinLocation = null
                                        mapViewRef?.invalidate()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Pin")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.selectMapPin(pin)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Set Destination ➔", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        showAddPlaceDialog = true
                                    },
                                    modifier = Modifier.height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // SAVED PLACES Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED PLACES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    TextButton(
                        onClick = { showAddPlaceDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Add Place", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (favorites.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddPlaceDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📍", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No saved places yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Save Home, Work, or gym for 1-tap alarms.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "+ Add",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        favorites.forEach { fav ->
                            val emoji = when (fav.category.uppercase()) {
                                "HOME" -> "🏠"
                                "WORK" -> "🏢"
                                "GYM" -> "🏋️"
                                "STATION", "TRANSIT" -> "🚉"
                                "AIRPORT" -> "✈️"
                                else -> "📍"
                            }
                            val radiusLabel = if (fav.radiusMeters >= 1000f) "${(fav.radiusMeters / 1000f).toInt()}km" else "${fav.radiusMeters.toInt()}m"

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        viewModel.selectFavorite(fav)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(emoji, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fav.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${fav.category.lowercase().replaceFirstChar { it.uppercase() }} • Alarm @ $radiusLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }

                                    // Edit & Delete actions
                                    IconButton(
                                        onClick = { editingPlaceEntity = fav },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Place",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteFavorite(fav.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Place",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // COMMUTE HISTORY Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMMUTE HISTORY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    if (recentTrips.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearRecentTrips() },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (recentTrips.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentTrips.forEach { trip ->
                            val emoji = if (trip.vehicleMode == "BUS") "🚌" else "🚗"
                            val radiusStr = if (trip.radiusMeters >= 1000f) "${(trip.radiusMeters / 1000f).toInt()}km" else "${trip.radiusMeters.toInt()}m"
                            SuggestionChip(
                                onClick = { viewModel.selectRecentTrip(trip) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(emoji, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = trip.destinationName,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Alarm @ $radiusStr",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                )
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Your completed alarms and commute trips will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // Add Saved Place Dialog
    if (showAddPlaceDialog) {
        var placeName by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf("HOME") }
        var selectedRadius by remember { mutableStateOf(500f) }
        val targetPoint = droppedPinLocation ?: currentMapCenter

        AlertDialog(
            onDismissRequest = { showAddPlaceDialog = false },
            title = {
                Text(
                    text = "Add Saved Place",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text("Place Name") },
                        placeholder = { Text("e.g. My Apartment, HQ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val categories = listOf("HOME" to "🏠 Home", "WORK" to "🏢 Work", "GYM" to "🏋️ Gym", "CUSTOM" to "📍 Other")
                        categories.forEach { (cat, label) ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("Alarm Distance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(200f to "200m", 500f to "500m", 1000f to "1km", 2000f to "2km").forEach { (rad, radLabel) ->
                            FilterChip(
                                selected = selectedRadius == rad,
                                onClick = { selectedRadius = rad },
                                label = { Text(radLabel, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text(
                        text = String.format("Location: %.4f° N, %.4f° E", targetPoint.latitude, targetPoint.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameToSave = placeName.ifBlank {
                            when (selectedCategory) {
                                "HOME" -> "Home"
                                "WORK" -> "Work"
                                "GYM" -> "Gym"
                                else -> "Saved Place"
                            }
                        }
                        viewModel.saveFavorite(
                            name = nameToSave,
                            category = selectedCategory,
                            latitude = targetPoint.latitude,
                            longitude = targetPoint.longitude,
                            radiusMeters = selectedRadius
                        )
                        showAddPlaceDialog = false
                    }
                ) {
                    Text("Save Place")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPlaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Saved Place Dialog
    editingPlaceEntity?.let { place ->
        var editName by remember(place) { mutableStateOf(place.name) }
        var editCategory by remember(place) { mutableStateOf(place.category) }
        var editRadius by remember(place) { mutableStateOf(place.radiusMeters) }
        var editNote by remember(place) { mutableStateOf(place.reminderNote) }

        AlertDialog(
            onDismissRequest = { editingPlaceEntity = null },
            title = {
                Text(
                    text = "Edit Saved Place",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Place Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val categories = listOf("HOME" to "🏠 Home", "WORK" to "🏢 Work", "GYM" to "🏋️ Gym", "CUSTOM" to "📍 Other")
                        categories.forEach { (cat, label) ->
                            FilterChip(
                                selected = editCategory == cat,
                                onClick = { editCategory = cat },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("Alarm Distance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(200f to "200m", 500f to "500m", 1000f to "1km", 2000f to "2km").forEach { (rad, radLabel) ->
                            FilterChip(
                                selected = editRadius == rad,
                                onClick = { editRadius = rad },
                                label = { Text(radLabel, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("Reminder Note (Optional)") },
                        placeholder = { Text("e.g. Bring office badge") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = place.copy(
                            name = editName.ifBlank { place.name },
                            category = editCategory,
                            radiusMeters = editRadius,
                            reminderNote = editNote
                        )
                        viewModel.updateFavorite(updated)
                        editingPlaceEntity = null
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.deleteFavorite(place.id)
                            editingPlaceEntity = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                    TextButton(onClick = { editingPlaceEntity = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}



@Composable
private fun SearchResultRow(
    item: SearchResultItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (item.type) {
            SearchResultType.METRO -> Icons.Default.DirectionsSubway to Color(0xFF673AB7)
            SearchResultType.BUS -> Icons.Default.DirectionsBus to Color(0xFF00897B)
            SearchResultType.ONLINE_PLACE -> Icons.Default.Place to Color(0xFF1E88E5)
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


