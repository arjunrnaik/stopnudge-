package com.wakeme.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import org.osmdroid.util.GeoPoint

data class SelectedBusStop(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val routes: String? = null
)

class WakeMeViewModel(application: Application) : AndroidViewModel(application) {

    private val transitDao = TransitDatabase.getInstance(application).transitDao()
    private val favoriteDao = WakeMeUserDatabase.getInstance(application).favoritePlaceDao()
    private val recentTripDao = WakeMeUserDatabase.getInstance(application).recentTripDao()
    private val searchRepository = SearchRepository(application)
    private var searchJob: Job? = null

    // Screen Navigation State (Uber/Rapido 3-Step Flow)
    private val _currentScreen = MutableStateFlow(WakeMeScreen.SEARCH_LANDING)
    val currentScreen: StateFlow<WakeMeScreen> = _currentScreen.asStateFlow()

    // Vehicle Mode State (Car / Cab vs City Bus)
    private val _selectedVehicleMode = MutableStateFlow(VehicleMode.BUS)
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

    private val _radiusMeters = MutableStateFlow(500f)
    val radiusMeters: StateFlow<Float> = _radiusMeters.asStateFlow()

    private val _alertMode = MutableStateFlow(AlertMode.WAKEUP_ALARM)
    val alertMode: StateFlow<AlertMode> = _alertMode.asStateFlow()

    private val _audioRouting = MutableStateFlow(AudioRouting.BOTH)
    val audioRouting: StateFlow<AudioRouting> = _audioRouting.asStateFlow()

    private val _vibrateEnabled = MutableStateFlow(true)
    val vibrateEnabled: StateFlow<Boolean> = _vibrateEnabled.asStateFlow()

    private val _reminderNote = MutableStateFlow("")
    val reminderNote: StateFlow<String> = _reminderNote.asStateFlow()

    private val _showMetroOverlay = MutableStateFlow(false)
    val showMetroOverlay: StateFlow<Boolean> = _showMetroOverlay.asStateFlow()

    private val _showBmtcOverlay = MutableStateFlow(false)
    val showBmtcOverlay: StateFlow<Boolean> = _showBmtcOverlay.asStateFlow()

    private val _showTransitOverlay = MutableStateFlow(false)
    val showTransitOverlay: StateFlow<Boolean> = _showTransitOverlay.asStateFlow()

    private val _metroStations = MutableStateFlow<List<MetroStationEntity>>(emptyList())
    val metroStations: StateFlow<List<MetroStationEntity>> = _metroStations.asStateFlow()

    private val _nearestMetroStation = MutableStateFlow<NammaMetroNetwork.ActiveStation?>(null)
    val nearestMetroStation: StateFlow<NammaMetroNetwork.ActiveStation?> = _nearestMetroStation.asStateFlow()

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
        _cameraMoveTarget.tryEmit(geo)
        _currentScreen.value = WakeMeScreen.MODE_SELECTION
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
        _cameraMoveTarget.tryEmit(geo)
        _currentScreen.value = WakeMeScreen.MODE_SELECTION
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

        _cameraMoveTarget.tryEmit(geoPoint)
        _currentScreen.value = WakeMeScreen.MODE_SELECTION
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
        _cameraMoveTarget.tryEmit(geoPoint)
        _currentScreen.value = WakeMeScreen.MODE_SELECTION
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
                _cameraMoveTarget.tryEmit(geoPoint)
                _currentScreen.value = WakeMeScreen.MODE_SELECTION
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
                            _cameraMoveTarget.tryEmit(geoPoint)
                            _currentScreen.value = WakeMeScreen.MODE_SELECTION
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
        val (nearest, distMeters) = NammaMetroNetwork.findNearestActiveStation(origin)
        _nearestMetroStation.value = nearest
        _showMetroOverlay.value = true // Automatically show metro overlay & markings
        _cameraMoveTarget.tryEmit(nearest.geoPoint)

        val distFormatted = if (distMeters >= 1000) {
            String.format("%.1f km", distMeters / 1000.0)
        } else {
            "${distMeters.toInt()} m"
        }
        _userMessage.tryEmit("🚇 Nearest: ${nearest.name} ($distFormatted away)")
    }

    fun selectActiveMetroStation(station: NammaMetroNetwork.ActiveStation) {
        _nearestMetroStation.value = station
        _selectedBusStop.value = null
        _cameraMoveTarget.tryEmit(station.geoPoint)
    }

    fun clearNearestMetroStation() {
        _nearestMetroStation.value = null
    }

    fun selectBusStop(name: String, geoPoint: GeoPoint, routes: String? = null) {
        _selectedBusStop.value = SelectedBusStop(name, geoPoint.latitude, geoPoint.longitude, routes)
        _nearestMetroStation.value = null
        _cameraMoveTarget.tryEmit(geoPoint)
    }

    fun clearSelectedBusStop() {
        _selectedBusStop.value = null
    }

    fun findAndFocusNearestBusStop(fromLocation: GeoPoint?) {
        val origin = fromLocation ?: _targetLocation.value
        viewModelScope.launch(Dispatchers.IO) {
            val nearest = transitDao.findNearestBusStop(origin.latitude, origin.longitude)
                ?: return@launch
            val stopPoint = GeoPoint(nearest.latitude, nearest.longitude)
            _targetLocation.value = stopPoint
            _targetName.value = "${nearest.name} Bus Stop"
            _selectedVehicleMode.value = VehicleMode.BUS
            _showBmtcOverlay.value = true

            // Set selected bus stop so the quick card immediately shows
            _selectedBusStop.value = SelectedBusStop(
                name = nearest.name,
                latitude = nearest.latitude,
                longitude = nearest.longitude,
                routes = nearest.directionHint ?: nearest.ref
            )
            _nearestMetroStation.value = null

            // Fetch surrounding stops on both sides of the street in this vicinity
            val nearbyBuses = transitDao.getBusStopsInBoundingBox(
                minLat = nearest.latitude - 0.06,
                maxLat = nearest.latitude + 0.06,
                minLon = nearest.longitude - 0.06,
                maxLon = nearest.longitude + 0.06,
                limit = 200
            )
            _busStops.value = nearbyBuses
            _cameraMoveTarget.tryEmit(stopPoint)

            val dLat = Math.toRadians(nearest.latitude - origin.latitude)
            val dLon = Math.toRadians(nearest.longitude - origin.longitude)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(origin.latitude)) * Math.cos(Math.toRadians(nearest.latitude)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val distMeters = 6371000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

            val distFormatted = if (distMeters >= 1000) {
                String.format("%.1f km", distMeters / 1000.0)
            } else {
                "${distMeters.toInt()} m"
            }
            _userMessage.tryEmit("🚌 Nearest Stop: ${nearest.name} ($distFormatted away)")
        }
    }

    fun updateBmtcBoundingBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        if (!_showBmtcOverlay.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stops = transitDao.getBusStopsInBoundingBox(minLat, maxLat, minLon, maxLon, limit = 200)
                _busStops.value = stops
            } catch (_: Exception) {}
        }
    }

    fun toggleBmtcOverlay(activeCenter: GeoPoint? = null) {
        val next = !_showBmtcOverlay.value
        _showBmtcOverlay.value = next
        if (!next) {
            _selectedBusStop.value = null
        } else {
            val center = activeCenter ?: _targetLocation.value
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val latDelta = 0.08
                    val lonDelta = 0.08
                    val nearbyBuses = transitDao.getBusStopsInBoundingBox(
                        minLat = center.latitude - latDelta,
                        maxLat = center.latitude + latDelta,
                        minLon = center.longitude - lonDelta,
                        maxLon = center.longitude + lonDelta,
                        limit = 200
                    )
                    _busStops.value = nearbyBuses
                } catch (_: Exception) {}
            }
            _userMessage.tryEmit("🚌 BMTC Bus stops enabled")
        }
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
        return DestinationConfig(
            name = _targetName.value,
            latitude = target.latitude,
            longitude = target.longitude,
            radiusMeters = _radiusMeters.value,
            vehicleMode = _selectedVehicleMode.value,
            alertMode = _alertMode.value,
            audioRouting = _audioRouting.value,
            reminderNote = _reminderNote.value,
            vibrate = _vibrateEnabled.value
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
