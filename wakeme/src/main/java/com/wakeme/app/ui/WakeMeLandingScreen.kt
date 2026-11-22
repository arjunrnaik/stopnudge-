package com.wakeme.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.MotionEvent
import java.net.URLEncoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.graphics.Paint
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.BmtcBusNetwork
import org.osmdroid.views.overlay.Polyline
import com.wakeme.app.data.SearchResultItem
import com.wakeme.app.data.SearchResultType
import com.wakeme.app.data.local.favorites.FavoritePlaceEntity
import com.wakeme.app.data.local.favorites.RecentTripEntity
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
 * Screen 1: WakeMe Clean Map & Search Landing Screen.
 * Implements the user's approved notebook sketch design:
 * - Top Search bar with Speech Mic icon + Profile/Settings button.
 * - [★ Saved Places] and [⏱ History] filter pills.
 * - Clean full-screen map with blue GPS dot, tap anywhere to drop pin.
 * - Right-side 3-FAB column: GPS Re-center, Metro toggle, BMTC toggle.
 * - Dimmed backdrop modals for Saved Places (tap star/long-press to remove with confirmation)
 *   and Commute History (distinct places, latest at top, no stars).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val showMetroOverlay by viewModel.showMetroOverlay.collectAsState()
    val showBmtcOverlay by viewModel.showBmtcOverlay.collectAsState()
    val metroStations by viewModel.metroStations.collectAsState()
    val nearestMetroStation by viewModel.nearestMetroStation.collectAsState()
    val busStops by viewModel.busStops.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recentTrips by viewModel.recentTrips.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayRef by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var currentMapCenter by remember { mutableStateOf(targetLocation) }
    var metroMarkersList by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var metroPolylinesList by remember { mutableStateOf<List<Polyline>>(emptyList()) }
    var busMarkersList by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var busPolylinesList by remember { mutableStateOf<List<Polyline>>(emptyList()) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Tap-to-drop pin state
    var droppedPinLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var droppedPinMarker by remember { mutableStateOf<Marker?>(null) }

    // Modals & Dialogs state
    var showSavedPlacesModal by remember { mutableStateOf(false) }
    var showHistoryModal by remember { mutableStateOf(false) }
    var showProfileSettingsDialog by remember { mutableStateOf(false) }
    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var favoriteToRemove by remember { mutableStateOf<FavoritePlaceEntity?>(null) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    // Speech-to-text recognition launcher for Mic button
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onSearchQueryChanged(spoken)
            }
        }
    }

    // Sync camera animations when target changes from search or favorites
    LaunchedEffect(viewModel) {
        viewModel.cameraMoveTarget.collect { target ->
            mapViewRef?.controller?.animateTo(target)
        }
    }

    // Update Metro track lines & station markings when overlay toggled
    LaunchedEffect(showMetroOverlay) {
        val map = mapViewRef ?: return@LaunchedEffect
        metroMarkersList.forEach { map.overlays.remove(it) }
        metroMarkersList = emptyList()
        metroPolylinesList.forEach { map.overlays.remove(it) }
        metroPolylinesList = emptyList()

        if (showMetroOverlay) {
            // 1. Draw 4 authentic Metro Track Polylines with exact line colors
            val trackDefs = listOf(
                Pair(NammaMetroNetwork.TRACK_PURPLE, NammaMetroNetwork.COLOR_PURPLE),
                Pair(NammaMetroNetwork.TRACK_GREEN, NammaMetroNetwork.COLOR_GREEN),
                Pair(NammaMetroNetwork.TRACK_YELLOW, NammaMetroNetwork.COLOR_YELLOW),
                Pair(NammaMetroNetwork.TRACK_PINK, NammaMetroNetwork.COLOR_PINK)
            )

            val newPolylines = mutableListOf<Polyline>()
            trackDefs.forEach { (points, color) ->
                val poly = Polyline(map).apply {
                    setPoints(points)
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = 9.5f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    outlinePaint.isAntiAlias = true
                }
                map.overlays.add(poly)
                newPolylines.add(poly)
            }
            metroPolylinesList = newPolylines

            // 2. Add Metro Station Node Badges with Station Name Pill Labels
            val anchorY = LocationMarkerUtils.getStationMarkerAnchorY(context)
            val newMarkers = mutableListOf<Marker>()
            NammaMetroNetwork.ACTIVE_STATIONS.forEach { station ->
                val lineColor = NammaMetroNetwork.getLineColor(station.line)
                val badgeDrawable = LocationMarkerUtils.createStationMarkerDrawable(
                    context = context,
                    stationName = station.name,
                    lineColor = lineColor,
                    isInterchange = station.isInterchange
                )
                val marker = Marker(map).apply {
                    position = station.geoPoint
                    title = "${station.name} Metro Station"
                    snippet = "Line: ${station.line}"
                    icon = badgeDrawable
                    setAnchor(Marker.ANCHOR_CENTER, anchorY)
                    setOnMarkerClickListener { _, _ ->
                        viewModel.selectActiveMetroStation(station)
                        true
                    }
                }
                map.overlays.add(marker)
                newMarkers.add(marker)
            }
            metroMarkersList = newMarkers
            map.invalidate()
        } else {
            map.invalidate()
        }
    }

    // Update BMTC bus stop markers and 50/50 two-tone corridor polylines when overlay toggled
    LaunchedEffect(showBmtcOverlay, busStops) {
        val map = mapViewRef ?: return@LaunchedEffect
        busMarkersList.forEach { map.overlays.remove(it) }
        busMarkersList = emptyList()
        busPolylinesList.forEach { map.overlays.remove(it) }
        busPolylinesList = emptyList()

        if (showBmtcOverlay) {
            val newMarkers = mutableListOf<Marker>()
            val anchorY = LocationMarkerUtils.getStationMarkerAnchorY(context)

            // Render prominent Deep Teal BMTC bus stops with station name pills on BOTH sides of boarding stations
            busStops.forEach { stop ->
                val marker = Marker(map).apply {
                    position = GeoPoint(stop.latitude, stop.longitude)
                    title = "${stop.name} Bus Stop"
                    snippet = stop.directionHint ?: "BMTC Bus Stop"
                    icon = LocationMarkerUtils.createBmtcBusStopMarkerDrawable(context, stop.name, "#00838F")
                    setAnchor(Marker.ANCHOR_CENTER, anchorY)
                    setOnMarkerClickListener { _, _ ->
                        viewModel.selectMapPin(
                            GeoPoint(stop.latitude, stop.longitude),
                            "${stop.name} Bus Stop"
                        )
                        true
                    }
                }
                map.overlays.add(marker)
                newMarkers.add(marker)
            }

            busMarkersList = newMarkers
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

                                val pinIcon = LocationMarkerUtils.createModernPinDrawable(ctx)
                                if (droppedPinMarker == null) {
                                    val marker = Marker(mapView).apply {
                                        position = geo
                                        title = "Selected Location"
                                        snippet = "Tap anywhere to move pin"
                                        icon = pinIcon
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    }
                                    mapView.overlays.add(marker)
                                    droppedPinMarker = marker
                                } else {
                                    droppedPinMarker?.position = geo
                                    droppedPinMarker?.icon = pinIcon
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
                                if (showBmtcOverlay) {
                                    val delta = 0.035
                                    viewModel.updateBmtcBoundingBox(
                                        minLat = center.latitude - delta,
                                        maxLat = center.latitude + delta,
                                        minLon = center.longitude - delta,
                                        maxLon = center.longitude + delta
                                    )
                                }
                            }
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            debounceJob?.cancel()
                            debounceJob = coroutineScope.launch {
                                delay(200)
                                val center = mapCenter as? GeoPoint ?: return@launch
                                currentMapCenter = center
                                if (showBmtcOverlay) {
                                    val delta = 0.035
                                    viewModel.updateBmtcBoundingBox(
                                        minLat = center.latitude - delta,
                                        maxLat = center.latitude + delta,
                                        minLon = center.longitude - delta,
                                        maxLon = center.longitude + delta
                                    )
                                }
                            }
                            return true
                        }
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

        // 2. Top Section: Search Bar + Voice Mic + Profile/Settings Button + Saved Places & History Pills
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row containing Search Bar and Profile/Setting Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "Where are you heading?" Search Bar with Mic inside
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .shadow(8.dp, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    "Where are you heading?",
                                    style = MaterialTheme.typography.bodyMedium,
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

                        // Voice Mic Button
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your destination...")
                                    }
                                    speechLauncher.launch(intent)
                                } catch (_: Exception) {
                                    viewModel.showMessage("Voice input not supported on this device")
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Profile / Settings Button (from sketch: Profile/setting future)
                Surface(
                    onClick = { showProfileSettingsDialog = true },
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(8.dp, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile and Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
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
                                    if (result.type == SearchResultType.GOOGLE_MAPS_SEARCH) {
                                        val gmapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(searchQuery, "UTF-8"))
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmapsUri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (_: Exception) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, gmapsUri))
                                        }
                                    } else {
                                        viewModel.onSearchResultSelected(result)
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // Two Filter Pills: [★ Saved Places] and [⏱ History]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Saved Places Pill
                Surface(
                    onClick = { showSavedPlacesModal = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFA000), // Gold Star
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Saved Places",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (favorites.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(1.dp)
                            ) {
                                Text(
                                    text = "${favorites.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // History Pill
                Surface(
                    onClick = { showHistoryModal = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (recentTrips.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(1.dp)
                            ) {
                                Text(
                                    text = "${recentTrips.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Right-Side 3-FAB Column (from sketch: Current location, Metro, BMTC (future))
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // FAB 1 (Top): Re-center on GPS Location
            FloatingActionButton(
                onClick = {
                    val myLoc = locationOverlayRef?.myLocation
                    if (myLoc != null) {
                        mapViewRef?.controller?.animateTo(myLoc)
                        currentMapCenter = myLoc
                    } else {
                        viewModel.showMessage("Locating your GPS satellites...")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My GPS Location",
                    modifier = Modifier.size(24.dp)
                )
            }

            // FAB 2 (Middle): Metro Station Layer Toggle (Tap) & Nearest Metro Station (Long-press)
            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (showMetroOverlay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                viewModel.toggleMetroOverlay()
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val refLocation = droppedPinLocation 
                                    ?: locationOverlayRef?.myLocation 
                                    ?: currentMapCenter
                                viewModel.findAndFocusNearestMetroStation(refLocation)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsSubway,
                    contentDescription = "Toggle Metro Stations (Long press for Nearest)",
                    tint = if (showMetroOverlay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            // FAB 3 (Bottom): BMTC Bus Stop Layer Toggle (Tap) & Nearest Bus Stop (Long-press)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (showBmtcOverlay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                viewModel.toggleBmtcOverlay()
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val refLocation = droppedPinLocation 
                                    ?: locationOverlayRef?.myLocation 
                                    ?: currentMapCenter
                                viewModel.findAndFocusNearestBusStop(refLocation)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = "Toggle BMTC Bus Stops (Long press for Nearest)",
                    tint = if (showBmtcOverlay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 4. Bottom Dropped Pin Action Card (if user tapped map)
        val pin = droppedPinLocation
        if (pin != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.selectMapPin(pin)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
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
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 4b. Nearest / Selected Metro Station Quick Card
        val nearestMetro = nearestMetroStation
        if (nearestMetro != null && droppedPinLocation == null) {
            val lineColor = NammaMetroNetwork.getLineColor(nearestMetro.line)
            val metroColor = Color(lineColor)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(14.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.5.dp, metroColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                            Surface(
                                shape = CircleShape,
                                color = metroColor,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsSubway,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = nearestMetro.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = metroColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${nearestMetro.line} Line",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = metroColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Active Namma Metro Station",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.clearNearestMetroStation() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.selectMapPin(
                                    nearestMetro.geoPoint,
                                    "${nearestMetro.name} Metro Station"
                                )
                                viewModel.clearNearestMetroStation()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = metroColor
                            )
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set as Destination ➔", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // 5. SAVED PLACES MODAL DIALOG (with Dimmed Backdrop Scrim)
    if (showSavedPlacesModal) {
        Dialog(onDismissRequest = { showSavedPlacesModal = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFA000),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Saved Places",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { showSavedPlacesModal = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Add Place Button
                    OutlinedButton(
                        onClick = {
                            showSavedPlacesModal = false
                            showAddPlaceDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add New Saved Place", fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Saved Places List (or empty state)
                    if (favorites.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⭐", fontSize = 36.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No saved places yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Save Home, Work, or gym for 1-tap alarms.\nTap star or long press to remove anytime.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(favorites) { fav ->
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
                                        .combinedClickable(
                                            onClick = {
                                                showSavedPlacesModal = false
                                                viewModel.selectFavorite(fav)
                                            },
                                            onLongClick = {
                                                favoriteToRemove = fav
                                            }
                                        )
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
                                                text = "Alarm @ $radiusLabel",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }

                                        // Gold Star button: tapping star also triggers removal confirmation
                                        IconButton(
                                            onClick = { favoriteToRemove = fav },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Remove Star",
                                                tint = Color(0xFFFFA000),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 6. HISTORY MODAL DIALOG (Distinct places only, latest at top, no stars)
    if (showHistoryModal) {
        Dialog(onDismissRequest = { showHistoryModal = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Commute History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { showHistoryModal = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    if (recentTrips.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Distinct places • Latest at top",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { showClearHistoryConfirm = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // History items list (no stars, distinct places)
                    if (recentTrips.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⏱️", fontSize = 36.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No commute history yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Places you commute to will be automatically recorded here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recentTrips) { trip ->
                                val emoji = when (trip.vehicleMode) {
                                    "BUS" -> "🚌"
                                    "METRO" -> "🚇"
                                    else -> "🚗"
                                }
                                val radiusStr = if (trip.radiusMeters >= 1000f) "${(trip.radiusMeters / 1000f).toInt()}km" else "${trip.radiusMeters.toInt()}m"

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showHistoryModal = false
                                            viewModel.selectRecentTrip(trip)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(emoji, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = trip.destinationName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Alarm @ $radiusStr",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Select",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 7. REMOVE SAVED PLACE CONFIRMATION DIALOG (from sketch: "tap on star to remove - a confirmation message")
    favoriteToRemove?.let { fav ->
        AlertDialog(
            onDismissRequest = { favoriteToRemove = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Remove Saved Place?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove \"${fav.name}\" from your saved places?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFavorite(fav.id)
                        viewModel.showMessage("Removed \"${fav.name}\" from saved places")
                        favoriteToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { favoriteToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 8. CLEAR HISTORY CONFIRMATION DIALOG
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = {
                Text("Clear Commute History?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to clear your recent commute history?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearRecentTrips()
                        showClearHistoryConfirm = false
                        viewModel.showMessage("Commute history cleared")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 9. PROFILE & SETTINGS DIALOG (from sketch: "Profile/setting future")
    if (showProfileSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showProfileSettingsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("WakeMe Settings & Profile", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Version: 2.0.0 (Preview)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "🛰️ Tracking: Hardware GNSS Satellite (0 Data consumed)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "🚇 Offline Transit: 66 Metro Stations & 8,000+ Bus Stops cached offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                viewModel.showMessage("Open System Settings > Battery Optimization")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Battery Optimization Settings", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showProfileSettingsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 10. ADD SAVED PLACE DIALOG
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
                        viewModel.showMessage("Saved \"$nameToSave\"")
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
}

@Composable
private fun SearchResultRow(
    item: SearchResultItem,
    onClick: () -> Unit
) {
    val isGmaps = item.type == SearchResultType.GOOGLE_MAPS_SEARCH

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isGmaps) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (item.type) {
            SearchResultType.METRO -> Icons.Default.DirectionsSubway to Color(0xFF673AB7)
            SearchResultType.BUS -> {
                val cat = BmtcBusCategory.fromRouteOrName(item.subtitle)
                Icons.Default.DirectionsBus to cat.composeColor
            }
            SearchResultType.ONLINE_PLACE -> Icons.Default.Place to Color(0xFF1E88E5)
            SearchResultType.GOOGLE_MAPS_SEARCH -> Icons.Default.OpenInNew to Color(0xFFEA4335)
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
                fontWeight = if (isGmaps) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isGmaps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitle.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isGmaps) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
