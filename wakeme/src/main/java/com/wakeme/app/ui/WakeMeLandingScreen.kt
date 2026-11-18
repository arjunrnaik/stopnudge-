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

    var showSavePinDialog by remember { mutableStateOf(false) }
    var savePinName by remember { mutableStateOf("") }

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

            // 1-Tap Quick Favorites Carousel
            QuickFavoritesRow(
                favorites = favorites,
                onFavoriteSelected = { fav ->
                    focusManager.clearFocus()
                    viewModel.selectFavorite(fav)
                },
                onAddFavorite = {
                    showSavePinDialog = true
                }
            )
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

        // 4. Bottom Section: Dropped Pin Card OR Commuter Suggestions
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val pin = droppedPinLocation
                if (pin != null) {
                    // Dropped Pin Action Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Dropped Pin Destination",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format("%.4f° N, %.4f° E (Tap to move)", pin.latitude, pin.longitude),
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
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Pin")
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.selectMapPin(pin)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set Destination & Choose Mode ➔",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Tap on map guide hint
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👆", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tap anywhere on the map to drop a destination pin.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Recent Commute Destinations (Uber/Rapido style)
                if (recentTrips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT DESTINATIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { viewModel.clearRecentTrips() },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }

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
                }

                // Popular Commuter Hubs Shortcut Row
                Text(
                    text = "POPULAR COMMUTER STOPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val popularHubs = listOf(
                        PopularHub("Silk Board", 12.9176, 77.6238, "🚌 Major Junction"),
                        PopularHub("Majestic Interchange", 12.9772, 77.5713, "🚇 Metro Hub"),
                        PopularHub("Indiranagar 100ft", 12.9784, 77.6408, "🚇 Purple Line"),
                        PopularHub("Koramangala Sony World", 12.9344, 77.6288, "🚌 Bus Hub"),
                        PopularHub("Electronic City Toll", 12.8452, 77.6602, "🚌 Elevated Hwy")
                    )

                    popularHubs.forEach { hub ->
                        SuggestionChip(
                            onClick = {
                                viewModel.selectMapPin(
                                    GeoPoint(hub.lat, hub.lon),
                                    hub.name
                                )
                            },
                            label = {
                                Column {
                                    Text(hub.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(hub.desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    }

    // Save Favorite Pin Dialog
    if (showSavePinDialog) {
        AlertDialog(
            onDismissRequest = { showSavePinDialog = false },
            title = { Text("Save Current Pin to Favorites") },
            text = {
                OutlinedTextField(
                    value = savePinName,
                    onValueChange = { savePinName = it },
                    placeholder = { Text("e.g. Grandma's House, Gym") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (savePinName.isNotBlank()) {
                            val pinToSave = droppedPinLocation ?: currentMapCenter
                            viewModel.saveCurrentAsFavorite(savePinName, location = pinToSave)
                            savePinName = ""
                            showSavePinDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class PopularHub(
    val name: String,
    val lat: Double,
    val lon: Double,
    val desc: String
)

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

@Composable
private fun QuickFavoritesRow(
    favorites: List<FavoritePlaceEntity>,
    onFavoriteSelected: (FavoritePlaceEntity) -> Unit,
    onAddFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Built-in / Saved Favorite items
        favorites.forEach { fav ->
            val icon = when (fav.category.uppercase()) {
                "HOME" -> "🏠"
                "WORK" -> "💼"
                "STATION" -> "🚉"
                "AIRPORT" -> "✈️"
                else -> "📍"
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onFavoriteSelected(fav) }
                    .shadow(3.dp, RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = fav.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Add Pin button
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onAddFavorite() }
                .shadow(2.dp, RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Save Pin",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Save Pin",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
