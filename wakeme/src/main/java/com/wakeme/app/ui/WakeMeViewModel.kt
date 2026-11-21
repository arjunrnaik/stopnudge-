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

    private val _nearestMetroStation = MutableStateFlow<MetroStationEntity?>(null)
    val nearestMetroStation: StateFlow<MetroStationEntity?> = _nearestMetroStation.asStateFlow()

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
        _cameraMoveTarget.tryEmit(geoPoint)
        _currentScreen.value = WakeMeScreen.MODE_SELECTION
    }

    fun selectMapPin(geoPoint: GeoPoint, name: String? = null) {
        _targetLocation.value = geoPoint
        _targetName.value = name ?: String.format("%.4f° N, %.4f° E", geoPoint.latitude, geoPoint.longitude)
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
        viewModelScope.launch(Dispatchers.Default) {
            val origin = fromLocation ?: _targetLocation.value
            var stations = _metroStations.value
            if (stations.isEmpty()) {
                try {
                    stations = transitDao.getAllMetroStations()
                    _metroStations.value = stations
                } catch (_: Exception) {}
            }
            if (stations.isEmpty()) {
                _userMessage.tryEmit("No metro stations found")
                return@launch
            }

            var closestStation: MetroStationEntity? = null
            var minDistanceMeters = Double.MAX_VALUE

            val results = FloatArray(1)
            for (station in stations) {
                android.location.Location.distanceBetween(
                    origin.latitude, origin.longitude,
                    station.latitude, station.longitude,
                    results
                )
                val dist = results[0].toDouble()
                if (dist < minDistanceMeters) {
                    minDistanceMeters = dist
                    closestStation = station
                }
            }

            closestStation?.let { nearest ->
                _nearestMetroStation.value = nearest
                _showMetroOverlay.value = true // Automatically show metro overlay
                val stationGeo = GeoPoint(nearest.latitude, nearest.longitude)
                _cameraMoveTarget.tryEmit(stationGeo)

                val distFormatted = if (minDistanceMeters >= 1000) {
                    String.format("%.1f km", minDistanceMeters / 1000.0)
                } else {
                    "${minDistanceMeters.toInt()} m"
                }
                _userMessage.tryEmit("🚇 Nearest: ${nearest.name} ($distFormatted away)")
            }
        }
    }

    fun clearNearestMetroStation() {
        _nearestMetroStation.value = null
    }

    fun toggleBmtcOverlay() {
        val next = !_showBmtcOverlay.value
        _showBmtcOverlay.value = next
        if (next) {
            if (_busStops.value.isEmpty()) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val initialBuses = transitDao.getInitialBusStops()
                        _busStops.value = initialBuses
                    } catch (_: Exception) {}
                }
            }
            _userMessage.tryEmit("🚌 BMTC Bus overlay enabled")
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
