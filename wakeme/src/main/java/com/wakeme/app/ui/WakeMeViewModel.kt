package com.wakeme.app.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.wakeme.app.data.MetroJourneyPlan
import com.wakeme.app.data.MetroRoutePlanner
import com.wakeme.app.data.RoadRoutingRepository
import com.wakeme.app.data.RouteResult
import com.wakeme.app.data.SearchResultItem
import com.wakeme.app.data.SearchResultType
import com.wakeme.app.data.SearchRepository
import com.wakeme.app.data.local.favorites.FavoritePlaceDao
import com.wakeme.app.data.local.favorites.FavoritePlaceEntity
import com.wakeme.app.data.local.favorites.RecentTripDao
import com.wakeme.app.data.local.favorites.RecentTripEntity
import com.wakeme.app.data.local.favorites.WakeMeUserDatabase
import com.wakeme.app.data.local.transit.BusStopEntity
import com.wakeme.app.data.local.transit.MetroStationEntity
import com.wakeme.app.data.local.transit.NammaMetroNetwork
import com.wakeme.app.data.local.transit.BmtcBusCategory
import com.wakeme.app.data.local.transit.BmtcBusNetwork
import com.wakeme.app.data.local.transit.TransitDatabase
import com.wakeme.app.model.AlertMode
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.model.DestinationConfig
import com.wakeme.app.model.VehicleMode
import com.wakeme.app.service.WakeMeTrackingService
import com.wakeme.app.util.GoogleMapsUrlParser
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.osmdroid.util.GeoPoint

data class SelectedBusStop(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val routes: String? = null,
    val direction: String? = null
)

class WakeMeViewModel(application: Application) : AndroidViewModel(application) {

    private val transitDao = TransitDatabase.getInstance(application).transitDao()
    private val favoriteDao = WakeMeUserDatabase.getInstance(application).favoritePlaceDao()
    private val recentTripDao = WakeMeUserDatabase.getInstance(application).recentTripDao()
    private val searchRepository = SearchRepository(application)
    private var searchJob: Job? = null
    private var previewRouteJob: Job? = null

    // Screen Navigation State (Uber/Rapido 3-Step Flow)
    private val _currentScreen = MutableStateFlow(WakeMeScreen.SEARCH_LANDING)
    val currentScreen: StateFlow<WakeMeScreen> = _currentScreen.asStateFlow()

    // Vehicle Mode State (Car / Cab vs City Bus)
    private val _selectedVehicleMode = MutableStateFlow(VehicleMode.METRO)
    val selectedVehicleMode: StateFlow<VehicleMode> = _selectedVehicleMode.asStateFlow()

    // BMTC Bus Category State (AC Volvo vs Normal Sarige vs Electric EV)
    private val _selectedBusCategory = MutableStateFlow(BmtcBusCategory.NORMAL_SARIGE)
    val selectedBusCategory: StateFlow<BmtcBusCategory> = _selectedBusCategory.asStateFlow()

    fun setBusCategory(category: BmtcBusCategory) {
        _selectedBusCategory.value = category
    }

    // Saved Favorites State
    private val _favorites = MutableStateFlow<List<FavoritePlaceEntity>>(emptyList())
    val favorites: StateFlow<List<FavoritePlaceEntity>> = _favorites.asStateFlow()

    // Recent Commute Trips History State
    private val _recentTrips = MutableStateFlow<List<RecentTripEntity>>(emptyList())
    val recentTrips: StateFlow<List<RecentTripEntity>> = _recentTrips.asStateFlow()

    // Default Bengaluru coordinates
    val defaultCenter = GeoPoint(12.9716, 77.5946)

    private val _targetLocation = MutableStateFlow(defaultCenter)
    val targetLocation: StateFlow<GeoPoint> = _targetLocation.asStateFlow()

    private val _targetName = MutableStateFlow("Bengaluru City Center")
    val targetName: StateFlow<String> = _targetName.asStateFlow()

    private val _radiusMeters = MutableStateFlow(800f)
    val radiusMeters: StateFlow<Float> = _radiusMeters.asStateFlow()

    private val _alertMode = MutableStateFlow(AlertMode.WAKEUP_ALARM)
    val alertMode: StateFlow<AlertMode> = _alertMode.asStateFlow()

    private val _audioRouting = MutableStateFlow(AudioRouting.BOTH)
    val audioRouting: StateFlow<AudioRouting> = _audioRouting.asStateFlow()

    private val _vibrateEnabled = MutableStateFlow(true)
    val vibrateEnabled: StateFlow<Boolean> = _vibrateEnabled.asStateFlow()

    private val _reminderNote = MutableStateFlow("")
    val reminderNote: StateFlow<String> = _reminderNote.asStateFlow()

    private val _showMetroOverlay = MutableStateFlow(true)
    val showMetroOverlay: StateFlow<Boolean> = _showMetroOverlay.asStateFlow()

    private val _showBmtcOverlay = MutableStateFlow(false)
    val showBmtcOverlay: StateFlow<Boolean> = _showBmtcOverlay.asStateFlow()

    private val _showTransitOverlay = MutableStateFlow(false)
    val showTransitOverlay: StateFlow<Boolean> = _showTransitOverlay.asStateFlow()

    private val _metroStations = MutableStateFlow<List<MetroStationEntity>>(emptyList())
    val metroStations: StateFlow<List<MetroStationEntity>> = _metroStations.asStateFlow()

    private val _nearestMetroStation = MutableStateFlow<NammaMetroNetwork.ActiveStation?>(null)
    val nearestMetroStation: StateFlow<NammaMetroNetwork.ActiveStation?> = _nearestMetroStation.asStateFlow()

    private val _nearestMetroRoadDistance = MutableStateFlow<Float?>(null)
    val nearestMetroRoadDistance: StateFlow<Float?> = _nearestMetroRoadDistance.asStateFlow()

    private val _nearestMetroRoadDuration = MutableStateFlow<Float?>(null)
    val nearestMetroRoadDuration: StateFlow<Float?> = _nearestMetroRoadDuration.asStateFlow()

    private val _nearestMetroRoutePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val nearestMetroRoutePoints: StateFlow<List<GeoPoint>> = _nearestMetroRoutePoints.asStateFlow()

    private val _isCalculatingRoadToMetro = MutableStateFlow(false)
    val isCalculatingRoadToMetro: StateFlow<Boolean> = _isCalculatingRoadToMetro.asStateFlow()

    private val _currentOriginLocation = MutableStateFlow<GeoPoint?>(null)
    val currentOriginLocation: StateFlow<GeoPoint?> = _currentOriginLocation.asStateFlow()

    private val _previewRoadRoute = MutableStateFlow<RouteResult?>(null)
    val previewRoadRoute: StateFlow<RouteResult?> = _previewRoadRoute.asStateFlow()

    private val _isLoadingPreviewRoute = MutableStateFlow(false)
    val isLoadingPreviewRoute: StateFlow<Boolean> = _isLoadingPreviewRoute.asStateFlow()

    private val _metroStopsBeforeAlert = MutableStateFlow(1)
    val metroStopsBeforeAlert: StateFlow<Int> = _metroStopsBeforeAlert.asStateFlow()

    private val _customBoardingStation = MutableStateFlow<NammaMetroNetwork.ActiveStation?>(null)
    val customBoardingStation: StateFlow<NammaMetroNetwork.ActiveStation?> = _customBoardingStation.asStateFlow()

    private val _metroJourneyPlan = MutableStateFlow<MetroJourneyPlan?>(null)
    val metroJourneyPlan: StateFlow<MetroJourneyPlan?> = _metroJourneyPlan.asStateFlow()

    private val _boardingRoadRoute = MutableStateFlow<RouteResult?>(null)
    val boardingRoadRoute: StateFlow<RouteResult?> = _boardingRoadRoute.asStateFlow()

    private val _exitRoadRoute = MutableStateFlow<RouteResult?>(null)
    val exitRoadRoute: StateFlow<RouteResult?> = _exitRoadRoute.asStateFlow()

    private val _selectedBusStop = MutableStateFlow<SelectedBusStop?>(null)
    val selectedBusStop: StateFlow<SelectedBusStop?> = _selectedBusStop.asStateFlow()

    private val _busStops = MutableStateFlow<List<BusStopEntity>>(emptyList())
    val busStops: StateFlow<List<BusStopEntity>> = _busStops.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    // Unified Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Event emitted when the map camera should animate to a specific point
    private val _cameraMoveTarget = MutableSharedFlow<GeoPoint>(extraBufferCapacity = 1)
    val cameraMoveTarget: SharedFlow<GeoPoint> = _cameraMoveTarget.asSharedFlow()

    // User feedback message event (e.g. Toasts)
    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        // Preload metro stations in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val metros = transitDao.getAllMetroStations()
                _metroStations.value = metros
            } catch (_: Exception) {}
        }

        // Bus data removed - pure Namma Metro focus

        // Collect user saved favorites
        viewModelScope.launch(Dispatchers.IO) {
            favoriteDao.getAllFavorites().collect { list ->
                _favorites.value = list
            }
        }

        // Collect recent trip history (distinct places, latest at top)
        viewModelScope.launch(Dispatchers.IO) {
            recentTripDao.getRecentTrips().collect { list ->
                _recentTrips.value = list.distinctBy { it.destinationName.trim().lowercase() }
            }
        }

        // Sync with background tracking service state
        viewModelScope.launch {
            WakeMeTrackingService.isTracking.collect { tracking ->
                _isTrackingActive.value = tracking
                if (tracking && _currentScreen.value != WakeMeScreen.ACTIVE_TRIP) {
                    _currentScreen.value = WakeMeScreen.ACTIVE_TRIP
                } else if (!tracking && _currentScreen.value == WakeMeScreen.ACTIVE_TRIP) {
                    _currentScreen.value = WakeMeScreen.SEARCH_LANDING
                }
            }
        }

        viewModelScope.launch {
            WakeMeTrackingService.activeConfig.collect { config ->
                if (config != null) {
                    _targetName.value = config.name
                    _targetLocation.value = GeoPoint(config.latitude, config.longitude)
                    _radiusMeters.value = config.radiusMeters
                    _selectedVehicleMode.value = config.vehicleMode
                    _alertMode.value = config.alertMode
                    _audioRouting.value = config.audioRouting
                    _vibrateEnabled.value = config.vibrate
                    _reminderNote.value = config.reminderNote
                    _metroStopsBeforeAlert.value = config.metroStopsBeforeAlert
                }
            }
        }
    }

    // --- Navigation ---

    fun navigateTo(screen: WakeMeScreen) {
        _currentScreen.value = screen
    }

    fun setVehicleMode(mode: VehicleMode) {
        _selectedVehicleMode.value = mode
        _alertMode.value = mode.alertMode
        _radiusMeters.value = mode.defaultRadiusMeters
        refreshPreviewRoadRoute()
    }

    fun setMetroStopsBeforeAlert(stops: Int) {
        _metroStopsBeforeAlert.value = stops.coerceIn(0, 5)
    }

    fun setCustomBoardingStation(station: NammaMetroNetwork.ActiveStation?) {
        _customBoardingStation.value = station
        _boardingRoadRoute.value = null
        refreshPreviewRoadRoute()
    }

    fun updateCurrentOrigin(point: GeoPoint?) {
        if (point != null) {
            _currentOriginLocation.value = point
        }
    }

    fun refreshPreviewRoadRoute() {
        previewRouteJob?.cancel()
        val dest = _targetLocation.value
        val mode = _selectedVehicleMode.value
        previewRouteJob = viewModelScope.launch {
            _isLoadingPreviewRoute.value = true
            try {
                val origin = _currentOriginLocation.value ?: fetchLastKnownOrigin()?.also {
                    _currentOriginLocation.value = it
                }
                if (origin != null && mode.usesRoadDistanceTrigger) {
                    val route = RoadRoutingRepository.getRoute(
                        origin.latitude,
                        origin.longitude,
                        dest.latitude,
                        dest.longitude
                    )
                    _previewRoadRoute.value = route
                } else {
                    _previewRoadRoute.value = null
                }

                // Always build Metro stop-to-stop plan when METRO mode is active
                if (mode == VehicleMode.METRO) {
                    val exitStation = NammaMetroNetwork.findNearestActiveStation(dest).first
                    val boardingStation = _customBoardingStation.value
                        ?: if (origin != null) {
                            RoadRoutingRepository.findNearestMetroStationByRoad(origin).station
                        } else {
                            NammaMetroNetwork.findNearestActiveStation(dest).first
                        }
                    val plan = MetroRoutePlanner.planJourney(
                        boardingStation = boardingStation,
                        exitStation = exitStation,
                        destinationPinName = _targetName.value,
                        destinationPinPoint = dest
                    )
                    _metroJourneyPlan.value = plan

                    if (origin != null) {
                        val distToBoard = RoadRoutingRepository.haversineDistanceMeters(
                            origin.latitude, origin.longitude,
                            boardingStation.latitude, boardingStation.longitude
                        )
                        if (distToBoard > MetroRoutePlanner.BOARDING_PROXIMITY_METERS) {
                            _boardingRoadRoute.value = RoadRoutingRepository.getRoute(
                                origin.latitude, origin.longitude,
                                boardingStation.latitude, boardingStation.longitude
                            )
                        } else {
                            _boardingRoadRoute.value = null
                        }
                    } else {
                        _boardingRoadRoute.value = null
                    }

                    // Stage 3: Fetch real road route from exit Metro station to Point B if > 50m away
                    if (plan.exitWalkDistanceMeters > MetroRoutePlanner.BOARDING_PROXIMITY_METERS) {
                        _exitRoadRoute.value = RoadRoutingRepository.getRoute(
                            exitStation.latitude, exitStation.longitude,
                            dest.latitude, dest.longitude
                        )
                    } else {
                        _exitRoadRoute.value = null
                    }
                }
            } catch (_: Exception) {
                _previewRoadRoute.value = null
                _boardingRoadRoute.value = null
                _exitRoadRoute.value = null
            } finally {
                _isLoadingPreviewRoute.value = false
            }
        }
    }

    private suspend fun fetchLastKnownOrigin(): GeoPoint? {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        return suspendCancellableCoroutine { cont ->
            try {
                LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) {
                            cont.resume(loc?.let { GeoPoint(it.latitude, it.longitude) })
                        }
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun openModeSelectionForTarget(geoPoint: GeoPoint) {
        _cameraMoveTarget.tryEmit(geoPoint)
        _previewRoadRoute.value = null
        _customBoardingStation.value = null
        _metroJourneyPlan.value = null
        _boardingRoadRoute.value = null
        _exitRoadRoute.value = null
        _currentScreen.value = WakeMeScreen.MODE_SELECTION
        refreshPreviewRoadRoute()
    }

    fun selectFavorite(fav: FavoritePlaceEntity) {
        val geo = GeoPoint(fav.latitude, fav.longitude)
        _targetLocation.value = geo
        _targetName.value = fav.name
        _radiusMeters.value = fav.radiusMeters
        try {
            _alertMode.value = AlertMode.valueOf(fav.alertMode)
        } catch (_: Exception) {}
        try {
            _audioRouting.value = AudioRouting.valueOf(fav.audioRouting)
        } catch (_: Exception) {}
        _reminderNote.value = fav.reminderNote
        openModeSelectionForTarget(geo)
    }

    fun selectRecentTrip(trip: RecentTripEntity) {
        val geo = GeoPoint(trip.latitude, trip.longitude)
        _targetLocation.value = geo
        _targetName.value = trip.destinationName
        _radiusMeters.value = trip.radiusMeters
        try {
            _selectedVehicleMode.value = VehicleMode.valueOf(trip.vehicleMode)
        } catch (_: Exception) {}
        try {
            _alertMode.value = AlertMode.valueOf(trip.alertMode)
        } catch (_: Exception) {}
        try {
            _audioRouting.value = AudioRouting.valueOf(trip.audioRouting)
        } catch (_: Exception) {}
        _reminderNote.value = trip.reminderNote
        openModeSelectionForTarget(geo)
    }

    fun clearRecentTrips() {
        viewModelScope.launch(Dispatchers.IO) {
            recentTripDao.clearRecentTrips()
        }
    }

    fun saveCurrentAsFavorite(customName: String, category: String = "CUSTOM", location: GeoPoint? = null) {
        val target = location ?: _targetLocation.value
        viewModelScope.launch(Dispatchers.IO) {
            val fav = FavoritePlaceEntity(
                name = customName.ifBlank { _targetName.value },
                category = category,
                latitude = target.latitude,
                longitude = target.longitude,
                radiusMeters = _radiusMeters.value,
                alertMode = _alertMode.value.name,
                audioRouting = _audioRouting.value.name,
                reminderNote = _reminderNote.value
            )
            favoriteDao.insertFavorite(fav)
        }
    }

    fun saveFavorite(
        name: String,
        category: String = "CUSTOM",
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 500f,
        reminderNote: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val fav = FavoritePlaceEntity(
                name = name,
                category = category,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                reminderNote = reminderNote
            )
            favoriteDao.insertFavorite(fav)
        }
    }

    fun deleteFavorite(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            favoriteDao.deleteById(id)
        }
    }

    fun updateFavorite(place: FavoritePlaceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            favoriteDao.updateFavorite(place)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        val center = _targetLocation.value
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250) // Debounce typing
            try {
                val results = searchRepository.search(
                    query = query,
                    centerLat = center.latitude,
                    centerLon = center.longitude
                )
                _searchResults.value = results
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun onSearchResultSelected(item: SearchResultItem) {
        val geoPoint = GeoPoint(item.latitude, item.longitude)
        _targetLocation.value = geoPoint
        _targetName.value = item.title
        _searchQuery.value = ""
        _searchResults.value = emptyList()

        if (item.type == SearchResultType.METRO) {
            _selectedVehicleMode.value = VehicleMode.METRO
        } else if (item.type == SearchResultType.BUS) {
            _selectedVehicleMode.value = VehicleMode.BUS
            _selectedBusCategory.value = BmtcBusCategory.fromRouteOrName(item.subtitle + " " + item.title)
        }

        openModeSelectionForTarget(geoPoint)
    }

    fun selectMapPin(geoPoint: GeoPoint, name: String? = null) {
        _targetLocation.value = geoPoint
        _targetName.value = name ?: String.format("%.4f° N, %.4f° E", geoPoint.latitude, geoPoint.longitude)
        if (name?.contains("Metro", ignoreCase = true) == true) {
            _selectedVehicleMode.value = VehicleMode.METRO
        } else if (name?.contains("Bus", ignoreCase = true) == true) {
            _selectedVehicleMode.value = VehicleMode.BUS
            _selectedBusCategory.value = BmtcBusCategory.fromRouteOrName(name)
        }
        openModeSelectionForTarget(geoPoint)
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun handleSharedText(sharedText: String) {
        viewModelScope.launch {
            val parsed = GoogleMapsUrlParser.parseSharedText(getApplication(), sharedText)
            if (parsed != null) {
                val geoPoint = GeoPoint(parsed.latitude, parsed.longitude)
                _targetLocation.value = geoPoint
                val name = parsed.name ?: "Shared Google Maps Location"
                _targetName.value = name
                openModeSelectionForTarget(geoPoint)
                _userMessage.tryEmit("Destination: $name")
            } else {
                // Try searching for place name extracted from text or URL
                val candidateName = extractSearchableQuery(sharedText)
                var resolved = false
                if (!candidateName.isNullOrBlank()) {
                    try {
                        val results = searchRepository.search(candidateName)
                        val top = results.firstOrNull { it.type != SearchResultType.GOOGLE_MAPS_SEARCH }
                        if (top != null) {
                            val geoPoint = GeoPoint(top.latitude, top.longitude)
                            _targetLocation.value = geoPoint
                            _targetName.value = top.title
                            openModeSelectionForTarget(geoPoint)
                            _userMessage.tryEmit("Destination: ${top.title}")
                            resolved = true
                        }
                    } catch (_: Exception) {}
                }

                if (!resolved) {
                    _userMessage.tryEmit("Could not find location in link. Tap on map to set pin.")
                }
            }
        }
    }

    private fun extractSearchableQuery(text: String): String? {
        val placeMatch = Regex("/place/([^/@?]+)").find(text)
        if (placeMatch != null) {
            val raw = placeMatch.groupValues[1].replace("+", " ")
            return try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        }
        return text.lines().map { it.trim() }.firstOrNull {
            it.isNotBlank() && !it.startsWith("http", ignoreCase = true) && !it.startsWith("intent:", ignoreCase = true)
        }
    }

    fun updateTargetLocation(geoPoint: GeoPoint, name: String? = null) {
        _targetLocation.value = geoPoint
        if (name != null) {
            _targetName.value = name
        } else {
            _targetName.value = String.format("%.4f° N, %.4f° E", geoPoint.latitude, geoPoint.longitude)
        }
    }

    fun setRadius(meters: Float) {
        _radiusMeters.value = meters.coerceIn(50f, 5000f)
    }

    fun setAlertMode(mode: AlertMode) {
        _alertMode.value = mode
    }

    fun setAudioRouting(routing: AudioRouting) {
        _audioRouting.value = routing
    }

    fun setVibrateEnabled(enabled: Boolean) {
        _vibrateEnabled.value = enabled
    }

    fun showMessage(msg: String) {
        _userMessage.tryEmit(msg)
    }

    fun setReminderNote(note: String) {
        _reminderNote.value = note
    }

    fun toggleMetroOverlay() {
        val next = !_showMetroOverlay.value
        _showMetroOverlay.value = next
        if (next) {
            _userMessage.tryEmit("🚇 Namma Metro overlay enabled")
        }
    }

    fun findAndFocusNearestMetroStation(fromLocation: GeoPoint?) {
        val origin = fromLocation ?: _targetLocation.value
        _isCalculatingRoadToMetro.value = true
        _userMessage.tryEmit("🔍 Finding nearest metro via road network...")

        viewModelScope.launch {
            try {
                val result = RoadRoutingRepository.findNearestMetroStationByRoad(origin)
                _nearestMetroStation.value = result.station
                _nearestMetroRoadDistance.value = result.roadDistanceMeters
                _nearestMetroRoadDuration.value = result.durationSeconds
                _nearestMetroRoutePoints.value = result.routePoints
                _showMetroOverlay.value = true
                _cameraMoveTarget.tryEmit(result.station.geoPoint)

                val distFormatted = if (result.roadDistanceMeters >= 1000) {
                    String.format(Locale.US, "%.1f km", result.roadDistanceMeters / 1000f)
                } else {
                    "${result.roadDistanceMeters.toInt()} m"
                }
                val minFormatted = (result.durationSeconds / 60).toInt().coerceAtLeast(1)
                _userMessage.tryEmit("🚇 Nearest: ${result.station.name} ($distFormatted by road • ~$minFormatted min)")
            } catch (_: Exception) {
                val (nearest, dist) = NammaMetroNetwork.findNearestActiveStation(origin)
                _nearestMetroStation.value = nearest
                _nearestMetroRoadDistance.value = dist.toFloat()
                _nearestMetroRoadDuration.value = (dist / 8.33).toFloat()
                _nearestMetroRoutePoints.value = emptyList()
                _showMetroOverlay.value = true
                _cameraMoveTarget.tryEmit(nearest.geoPoint)
            } finally {
                _isCalculatingRoadToMetro.value = false
            }
        }
    }

    fun selectActiveMetroStation(station: NammaMetroNetwork.ActiveStation) {
        _nearestMetroStation.value = station
        _selectedBusStop.value = null
        _nearestMetroRoadDistance.value = null
        _nearestMetroRoadDuration.value = null
        _nearestMetroRoutePoints.value = emptyList()
        _cameraMoveTarget.tryEmit(station.geoPoint)
    }

    fun clearNearestMetroStation() {
        _nearestMetroStation.value = null
        _nearestMetroRoadDistance.value = null
        _nearestMetroRoadDuration.value = null
        _nearestMetroRoutePoints.value = emptyList()
    }

    fun selectBusStop(name: String, geoPoint: GeoPoint, routes: String? = null, direction: String? = null) {
        _selectedBusStop.value = SelectedBusStop(name, geoPoint.latitude, geoPoint.longitude, routes, direction)
        _nearestMetroStation.value = null
        _cameraMoveTarget.tryEmit(geoPoint)
    }

    fun clearSelectedBusStop() {
        _selectedBusStop.value = null
    }

    fun findAndFocusNearestBusStop(fromLocation: GeoPoint?) {
        _userMessage.tryEmit("🚇 Showing nearest Namma Metro station")
        findAndFocusNearestMetroStation(fromLocation)
    }

    fun updateBmtcBoundingBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        // Bus data removed - pure Namma Metro focus
    }

    fun toggleBmtcOverlay(activeCenter: GeoPoint? = null) {
        _userMessage.tryEmit("🚌 BMTC bus network is paused. Focusing on Namma Metro.")
    }

    fun toggleTransitOverlay() {
        val next = !_showTransitOverlay.value
        _showTransitOverlay.value = next
        if (next && _busStops.value.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val initialBuses = transitDao.getInitialBusStops()
                    _busStops.value = initialBuses
                } catch (_: Exception) {}
            }
        }
    }

    fun getDestinationConfig(): DestinationConfig {
        val target = _targetLocation.value
        val boarding = _customBoardingStation.value ?: _metroJourneyPlan.value?.boardingStation
        return DestinationConfig(
            name = _targetName.value,
            latitude = target.latitude,
            longitude = target.longitude,
            radiusMeters = _radiusMeters.value,
            vehicleMode = _selectedVehicleMode.value,
            alertMode = _alertMode.value,
            audioRouting = _audioRouting.value,
            reminderNote = _reminderNote.value,
            vibrate = _vibrateEnabled.value,
            metroStopsBeforeAlert = _metroStopsBeforeAlert.value,
            metroBoardingLat = boarding?.latitude,
            metroBoardingLon = boarding?.longitude,
            metroBoardingName = boarding?.name
        )
    }

    fun startTracking(context: Context) {
        val config = getDestinationConfig()

        // Auto-persist into recent trips history
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recentTripDao.insertRecentTrip(
                    RecentTripEntity(
                        destinationName = config.name,
                        latitude = config.latitude,
                        longitude = config.longitude,
                        vehicleMode = config.vehicleMode.name,
                        radiusMeters = config.radiusMeters,
                        alertMode = config.alertMode.name,
                        audioRouting = config.audioRouting.name,
                        reminderNote = config.reminderNote
                    )
                )
                recentTripDao.trimOldTrips()
            } catch (_: Exception) {}
        }

        WakeMeTrackingService.start(context, config)
        _isTrackingActive.value = true
        _currentScreen.value = WakeMeScreen.ACTIVE_TRIP
    }

    fun stopTracking(context: Context) {
        WakeMeTrackingService.stop(context)
        _isTrackingActive.value = false
        _currentScreen.value = WakeMeScreen.SEARCH_LANDING
    }

    fun setTrackingActive(active: Boolean) {
        _isTrackingActive.value = active
    }
}
