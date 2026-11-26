package com.wakeme.app.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.wakeme.app.data.BmtcRouteOption
import com.wakeme.app.data.BmtcRoutePlanner
import com.wakeme.app.data.IndexedBmtcData
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
import com.wakeme.app.model.SelectedBusStop
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

    private val _nearestBusRoadDistance = MutableStateFlow<Float?>(null)
    val nearestBusRoadDistance: StateFlow<Float?> = _nearestBusRoadDistance.asStateFlow()

    private val _nearestBusRoadDuration = MutableStateFlow<Float?>(null)
    val nearestBusRoadDuration: StateFlow<Float?> = _nearestBusRoadDuration.asStateFlow()

    private val _nearestBusRoutePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val nearestBusRoutePoints: StateFlow<List<GeoPoint>> = _nearestBusRoutePoints.asStateFlow()

    private val _isCalculatingRoadToBus = MutableStateFlow(false)
    val isCalculatingRoadToBus: StateFlow<Boolean> = _isCalculatingRoadToBus.asStateFlow()

    private val _bmtcIndex = MutableStateFlow(IndexedBmtcData.EMPTY)
    private val _allBusStopsList = MutableStateFlow<List<BusStopEntity>>(emptyList())
    val allBusStopsList: StateFlow<List<BusStopEntity>> = _allBusStopsList.asStateFlow()

    private val _bmtcRouteOptions = MutableStateFlow<List<BmtcRouteOption>>(emptyList())
    val bmtcRouteOptions: StateFlow<List<BmtcRouteOption>> = _bmtcRouteOptions.asStateFlow()

    private val _selectedBusRouteIndex = MutableStateFlow(0)
    val selectedBusRouteIndex: StateFlow<Int> = _selectedBusRouteIndex.asStateFlow()

    private val _busLegRoadPoints = MutableStateFlow<Map<Int, List<GeoPoint>>>(emptyMap())
    val busLegRoadPoints: StateFlow<Map<Int, List<GeoPoint>>> = _busLegRoadPoints.asStateFlow()

    private val _customBoardingBusStop = MutableStateFlow<BusStopEntity?>(null)
    val customBoardingBusStop: StateFlow<BusStopEntity?> = _customBoardingBusStop.asStateFlow()

    private val _preferredSearchedBusRouteNo = MutableStateFlow<String?>(null)
    val preferredSearchedBusRouteNo: StateFlow<String?> = _preferredSearchedBusRouteNo.asStateFlow()

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
        // Immediately acquire FusedLocation cached GPS (<50ms) so map opens at user's location without waiting
        viewModelScope.launch {
            fetchLastKnownOrigin()?.let { pt ->
                _currentOriginLocation.value = pt
                if (_currentScreen.value == WakeMeScreen.SEARCH_LANDING && _targetName.value == "Bengaluru City Center") {
                    _targetLocation.value = pt
                }
            }
        }

        // Preload metro stations & BMTC stop/route graph in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val metros = transitDao.getAllMetroStations()
                _metroStations.value = metros
            } catch (_: Exception) {}
            try {
                ensureBmtcIndexLoaded()
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
        _customBoardingBusStop.value = null
        _boardingRoadRoute.value = null
        refreshPreviewRoadRoute()
    }

    fun setCustomBoardingBusStop(stop: BusStopEntity?) {
        _customBoardingBusStop.value = stop
        _customBoardingStation.value = null
        _boardingRoadRoute.value = null
        _selectedBusRouteIndex.value = 0
        refreshPreviewRoadRoute()
    }

    fun selectBusRouteOption(index: Int) {
        val options = _bmtcRouteOptions.value
        val option = options.getOrNull(index) ?: return
        _selectedBusRouteIndex.value = index
        _metroJourneyPlan.value = option.plan
        viewModelScope.launch {
            loadTransitPlanRoadSegments(
                plan = option.plan,
                origin = _currentOriginLocation.value ?: fetchLastKnownOrigin(),
                dest = _targetLocation.value,
                precomputedShapes = option.legShapePoints
            )
        }
    }

    private suspend fun ensureBmtcIndexLoaded(): IndexedBmtcData {
        val current = _bmtcIndex.value
        if (!current.isEmpty) return current
        val stops = transitDao.getAllBusStops()
        val routes = transitDao.getAllBusRoutes()
        val built = BmtcRoutePlanner.buildIndex(stops, routes)
        _bmtcIndex.value = built
        _allBusStopsList.value = stops.sortedByDescending { it.ref?.length ?: 0 }
        return built
    }

    private suspend fun loadTransitPlanRoadSegments(
        plan: MetroJourneyPlan,
        origin: GeoPoint?,
        dest: GeoPoint,
        precomputedShapes: Map<Int, List<GeoPoint>> = emptyMap()
    ) {
        if (origin != null) {
            val distToBoard = RoadRoutingRepository.haversineDistanceMeters(
                origin.latitude, origin.longitude,
                plan.boardingStation.latitude, plan.boardingStation.longitude
            )
            if (distToBoard > MetroRoutePlanner.BOARDING_PROXIMITY_METERS) {
                _boardingRoadRoute.value = RoadRoutingRepository.getRoute(
                    origin.latitude, origin.longitude,
                    plan.boardingStation.latitude, plan.boardingStation.longitude
                )
            } else {
                _boardingRoadRoute.value = null
            }
        } else {
            _boardingRoadRoute.value = null
        }

        if (plan.exitWalkDistanceMeters > MetroRoutePlanner.BOARDING_PROXIMITY_METERS) {
            _exitRoadRoute.value = RoadRoutingRepository.getRoute(
                plan.exitStation.latitude, plan.exitStation.longitude,
                dest.latitude, dest.longitude
            )
        } else {
            _exitRoadRoute.value = null
        }

        if (plan.isBusJourney) {
            val legMap = HashMap<Int, List<GeoPoint>>(plan.legs.size)
            plan.legs.forEachIndexed { idx, leg ->
                val snapped = precomputedShapes[idx]
                if (snapped != null && snapped.size >= 2) {
                    legMap[idx] = snapped
                } else if (leg.stations.size >= 2) {
                    val b = leg.boardingStation
                    val e = leg.alightingStation
                    try {
                        val routeRes = RoadRoutingRepository.getRoute(b.latitude, b.longitude, e.latitude, e.longitude)
                        if (routeRes.points.size >= 2) {
                            legMap[idx] = routeRes.points
                        }
                    } catch (_: Exception) {}
                }
            }
            _busLegRoadPoints.value = legMap
        } else {
            _busLegRoadPoints.value = emptyMap()
        }
    }

    fun updateCurrentOrigin(point: GeoPoint?) {
        if (point != null) {
            _currentOriginLocation.value = point
        }
    }

    fun requestImmediateLocationCenter(onLocationReady: (GeoPoint) -> Unit) {
        val cached = _currentOriginLocation.value
        if (cached != null) {
            onLocationReady(cached)
            return
        }
        viewModelScope.launch {
            val pt = fetchLastKnownOrigin()
            if (pt != null) {
                _currentOriginLocation.value = pt
                onLocationReady(pt)
            } else {
                _userMessage.tryEmit("Locating your GPS satellites...")
            }
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

                // Build 3-stage Stop-to-Stop plan for Metro or BMTC Bus
                if (mode == VehicleMode.METRO) {
                    _bmtcRouteOptions.value = emptyList()
                    _busLegRoadPoints.value = emptyMap()
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
                    loadTransitPlanRoadSegments(plan, origin, dest)
                } else if (mode == VehicleMode.BUS) {
                    val effectiveOrigin = origin ?: dest
                    val index = kotlinx.coroutines.withContext(Dispatchers.IO) { ensureBmtcIndexLoaded() }
                    val options = kotlinx.coroutines.withContext(Dispatchers.Default) {
                        BmtcRoutePlanner.planBusJourneys(
                            origin = effectiveOrigin,
                            destination = dest,
                            destinationName = _targetName.value,
                            index = index,
                            preferredBoardingStop = _customBoardingBusStop.value
                        )
                    }
                    _bmtcRouteOptions.value = options
                    val preferredRouteNo = _preferredSearchedBusRouteNo.value
                    val matchedPreferredIdx = if (!preferredRouteNo.isNullOrBlank()) {
                        options.indexOfFirst { it.routeTitle.equals(preferredRouteNo, ignoreCase = true) }
                    } else -1
                    val selIdx = if (matchedPreferredIdx >= 0) {
                        matchedPreferredIdx
                    } else {
                        _selectedBusRouteIndex.value.coerceIn(0, (options.size - 1).coerceAtLeast(0))
                    }
                    _selectedBusRouteIndex.value = selIdx
                    val chosenOpt = options.getOrNull(selIdx)
                    val chosenPlan = chosenOpt?.plan
                    _metroJourneyPlan.value = chosenPlan
                    if (chosenOpt != null) {
                        _selectedBusCategory.value = chosenOpt.category
                        loadTransitPlanRoadSegments(chosenOpt.plan, origin, dest, chosenOpt.legShapePoints)
                    } else {
                        _boardingRoadRoute.value = null
                        _exitRoadRoute.value = null
                        _busLegRoadPoints.value = emptyMap()
                    }
                } else {
                    _bmtcRouteOptions.value = emptyList()
                    _metroJourneyPlan.value = null
                    _boardingRoadRoute.value = null
                    _exitRoadRoute.value = null
                    _busLegRoadPoints.value = emptyMap()
                }
            } catch (_: Exception) {
                _previewRoadRoute.value = null
                _boardingRoadRoute.value = null
                _exitRoadRoute.value = null
                _busLegRoadPoints.value = emptyMap()
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
        _customBoardingBusStop.value = null
        _metroJourneyPlan.value = null
        _bmtcRouteOptions.value = emptyList()
        _selectedBusRouteIndex.value = 0
        _busLegRoadPoints.value = emptyMap()
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
        val resolvedName = customName.ifBlank { _targetName.value }
        val alreadySaved = _favorites.value.any {
            it.name.equals(resolvedName, ignoreCase = true) &&
                RoadRoutingRepository.haversineDistanceMeters(it.latitude, it.longitude, target.latitude, target.longitude) < 50.0
        }
        if (alreadySaved) return

        viewModelScope.launch(Dispatchers.IO) {
            val fav = FavoritePlaceEntity(
                name = resolvedName,
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
        val alreadySaved = _favorites.value.any {
            it.name.equals(name, ignoreCase = true) &&
                RoadRoutingRepository.haversineDistanceMeters(it.latitude, it.longitude, latitude, longitude) < 50.0
        }
        if (alreadySaved) return

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
            _preferredSearchedBusRouteNo.value = null
            _selectedVehicleMode.value = VehicleMode.METRO
        } else if (item.type == SearchResultType.BUS || item.type == SearchResultType.BUS_ROUTE) {
            _preferredSearchedBusRouteNo.value = item.routeNo
            _selectedVehicleMode.value = VehicleMode.BUS
            _selectedBusCategory.value = BmtcBusCategory.fromRouteOrName((item.routeNo ?: "") + " " + item.subtitle + " " + item.title)
        } else {
            _preferredSearchedBusRouteNo.value = null
        }

        openModeSelectionForTarget(geoPoint)
    }

    fun selectMapPin(geoPoint: GeoPoint, name: String? = null) {
        _preferredSearchedBusRouteNo.value = null
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
            _userMessage.tryEmit("Namma Metro overlay enabled")
        }
    }

    fun findAndFocusNearestMetroStation(fromLocation: GeoPoint?) {
        val origin = fromLocation ?: _targetLocation.value
        _isCalculatingRoadToMetro.value = true
        _userMessage.tryEmit("Finding nearest metro via road network...")

        viewModelScope.launch {
            try {
                val result = RoadRoutingRepository.findNearestMetroStationByRoad(origin)
                _selectedBusStop.value = null
                _nearestBusRoutePoints.value = emptyList()
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
                _userMessage.tryEmit("Nearest Metro: ${result.station.name} ($distFormatted by road • ~$minFormatted min)")
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
        _nearestBusRoadDistance.value = null
        _nearestBusRoadDuration.value = null
        _nearestBusRoutePoints.value = emptyList()
        _cameraMoveTarget.tryEmit(geoPoint)
    }

    fun clearSelectedBusStop() {
        _selectedBusStop.value = null
        _nearestBusRoadDistance.value = null
        _nearestBusRoadDuration.value = null
        _nearestBusRoutePoints.value = emptyList()
    }

    fun findAndFocusNearestBusStop(fromLocation: GeoPoint?) {
        val origin = fromLocation ?: _currentOriginLocation.value ?: _targetLocation.value
        _isCalculatingRoadToBus.value = true
        _userMessage.tryEmit("Finding nearest BMTC stop via road network...")

        viewModelScope.launch {
            try {
                val candidates = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    transitDao.findNearestBusStops(origin.latitude, origin.longitude, 8)
                }
                val result = RoadRoutingRepository.findNearestBusStopByRoad(origin, candidates)
                if (result != null) {
                    val stop = result.stop
                    _nearestMetroStation.value = null
                    _nearestMetroRoutePoints.value = emptyList()
                    _selectedBusStop.value = SelectedBusStop(
                        name = stop.name,
                        latitude = stop.latitude,
                        longitude = stop.longitude,
                        routes = stop.ref?.takeIf { it.isNotBlank() },
                        direction = stop.directionHint?.takeIf { it.isNotBlank() }
                    )
                    _nearestBusRoadDistance.value = result.roadDistanceMeters
                    _nearestBusRoadDuration.value = result.durationSeconds
                    _nearestBusRoutePoints.value = result.routePoints
                    _showBmtcOverlay.value = true
                    updateBmtcBoundingBox(
                        minLat = stop.latitude - 0.025,
                        maxLat = stop.latitude + 0.025,
                        minLon = stop.longitude - 0.025,
                        maxLon = stop.longitude + 0.025,
                        zoomLevel = 15.5
                    )
                    _cameraMoveTarget.tryEmit(GeoPoint(stop.latitude, stop.longitude))

                    val distFormatted = if (result.roadDistanceMeters >= 1000) {
                        String.format(Locale.US, "%.1f km", result.roadDistanceMeters / 1000f)
                    } else {
                        "${result.roadDistanceMeters.toInt()} m"
                    }
                    val minFormatted = (result.durationSeconds / 60).toInt().coerceAtLeast(1)
                    _userMessage.tryEmit("Nearest Stop: ${stop.name} ($distFormatted by road • ~$minFormatted min)")
                } else {
                    _userMessage.tryEmit("No BMTC stops found nearby")
                }
            } catch (_: Exception) {
                _userMessage.tryEmit("Could not locate nearest bus stop")
            } finally {
                _isCalculatingRoadToBus.value = false
            }
        }
    }

    fun updateBmtcBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        zoomLevel: Double = 15.0
    ) {
        if (!_showBmtcOverlay.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stops = if (zoomLevel < 14.5) {
                    transitDao.getMajorBusHubs(minRoutes = 55, limit = 75)
                } else {
                    transitDao.getBusStopsInBoundingBox(minLat, maxLat, minLon, maxLon, limit = 160)
                }
                _busStops.value = stops
            } catch (_: Exception) {}
        }
    }

    fun toggleBmtcOverlay(activeCenter: GeoPoint? = null, zoomLevel: Double = 15.0) {
        val next = !_showBmtcOverlay.value
        _showBmtcOverlay.value = next
        if (next) {
            val center = activeCenter ?: _currentOriginLocation.value ?: _targetLocation.value
            val delta = 0.03
            updateBmtcBoundingBox(
                minLat = center.latitude - delta,
                maxLat = center.latitude + delta,
                minLon = center.longitude - delta,
                maxLon = center.longitude + delta,
                zoomLevel = zoomLevel
            )
            _userMessage.tryEmit("BMTC Bus Stops ON (zoom in for both sides of road)")
        } else {
            _busStops.value = emptyList()
            _selectedBusStop.value = null
            _nearestBusRoutePoints.value = emptyList()
            _nearestBusRoadDistance.value = null
            _nearestBusRoadDuration.value = null
        }
    }

    fun getDestinationConfig(): DestinationConfig {
        val target = _targetLocation.value
        val customBus = _customBoardingBusStop.value?.let {
            NammaMetroNetwork.ActiveStation(it.id, it.name, "BMTC", it.latitude, it.longitude)
        }
        val boarding = customBus ?: _customBoardingStation.value ?: _metroJourneyPlan.value?.boardingStation
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

        if (config.vehicleMode == VehicleMode.METRO || config.vehicleMode == VehicleMode.BUS) {
            val bRoute = _boardingRoadRoute.value
            val eRoute = _exitRoadRoute.value
            WakeMeTrackingService.seedPreloadedMetroRoutes(
                plan = _metroJourneyPlan.value,
                boardingPoints = bRoute?.points.orEmpty(),
                boardingDistMeters = bRoute?.distanceMeters,
                boardingDurSeconds = bRoute?.durationSeconds,
                exitPoints = eRoute?.points.orEmpty(),
                exitDistMeters = eRoute?.distanceMeters,
                exitDurSeconds = eRoute?.durationSeconds,
                busLegRoadPoints = if (config.vehicleMode == VehicleMode.BUS) _busLegRoadPoints.value else emptyMap()
            )
        } else if (config.vehicleMode.usesRoadDistanceTrigger) {
            WakeMeTrackingService.seedPreloadedRoadRoute(_previewRoadRoute.value?.points.orEmpty())
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
