package com.stopnudge.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stopnudge.app.data.local.transit.BusStopEntity
import com.stopnudge.app.data.local.transit.MetroStationEntity
import com.stopnudge.app.data.local.transit.TransitDatabase
import com.stopnudge.app.data.local.user.SavedPlaceEntity
import com.stopnudge.app.data.local.user.TripEntity
import com.stopnudge.app.data.local.user.UserDatabase
import com.stopnudge.app.service.TrackingForegroundService
import com.stopnudge.app.util.GoogleMapsUrlParser
import com.stopnudge.app.util.ParsedCoordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectedDestination(
    val name: String,
    val subtitle: String? = null,
    val latitude: Double,
    val longitude: Double,
    val mode: String = "TRANSIT"
)

class StopNudgeViewModel(application: Application) : AndroidViewModel(application) {

    private val transitDao = TransitDatabase.getInstance(application).transitDao()
    private val userDao = UserDatabase.getInstance(application).userDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _metroStations = MutableStateFlow<List<MetroStationEntity>>(emptyList())
    val metroStations: StateFlow<List<MetroStationEntity>> = _metroStations.asStateFlow()

    private val _busStops = MutableStateFlow<List<BusStopEntity>>(emptyList())
    val busStops: StateFlow<List<BusStopEntity>> = _busStops.asStateFlow()

    private val _selectedDestination = MutableStateFlow<SelectedDestination?>(null)
    val selectedDestination: StateFlow<SelectedDestination?> = _selectedDestination.asStateFlow()

    private val _alertRadiusMeters = MutableStateFlow(500.0) // default 500m
    val alertRadiusMeters: StateFlow<Double> = _alertRadiusMeters.asStateFlow()

    val activeTrip: StateFlow<TripEntity?> = userDao.getActiveTripFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val savedPlaces: StateFlow<List<SavedPlaceEntity>> = userDao.getAllSavedPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tripHistory: StateFlow<List<TripEntity>> = userDao.getAllTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations.asStateFlow()

    init {
        loadInitialStops()
        checkBatteryOptimizationStatus()
    }

    private fun loadInitialStops() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val metros = transitDao.getAllMetroStations()
                val buses = transitDao.searchBusStops("Majestic")
                withContext(Dispatchers.Main) {
                    _metroStations.value = metros
                    _busStops.value = buses
                }
            } catch (e: Throwable) {
                android.util.Log.e("StopNudge", "Failed to load initial transit stops", e)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (query.isBlank()) {
                    val metros = transitDao.getAllMetroStations()
                    val buses = transitDao.searchBusStops("Majestic")
                    withContext(Dispatchers.Main) {
                        _metroStations.value = metros
                        _busStops.value = buses
                    }
                } else {
                    val metros = transitDao.searchMetroStations(query.trim())
                    val buses = transitDao.searchBusStops(query.trim())
                    withContext(Dispatchers.Main) {
                        _metroStations.value = metros
                        _busStops.value = buses
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("StopNudge", "Failed to search transit stops", e)
            }
        }
    }

    fun selectMetroStation(station: MetroStationEntity) {
        _selectedDestination.value = SelectedDestination(
            name = station.name,
            subtitle = "${station.metroLines} Line",
            latitude = station.latitude,
            longitude = station.longitude,
            mode = "METRO"
        )
    }

    fun selectBusStop(stop: BusStopEntity) {
        _selectedDestination.value = SelectedDestination(
            name = stop.name,
            subtitle = stop.operator ?: "BMTC Bus Stop",
            latitude = stop.latitude,
            longitude = stop.longitude,
            mode = "BUS"
        )
    }

    fun setCustomCoordinates(coords: ParsedCoordinates) {
        _selectedDestination.value = SelectedDestination(
            name = coords.label ?: "Pinned Location",
            subtitle = String.format("%.5f, %.5f", coords.latitude, coords.longitude),
            latitude = coords.latitude,
            longitude = coords.longitude,
            mode = "CUSTOM"
        )
    }

    fun setAlertRadius(radiusMeters: Double) {
        _alertRadiusMeters.value = radiusMeters
    }

    fun clearSelectedDestination() {
        _selectedDestination.value = null
    }

    fun startTracking(context: Context) {
        val destination = _selectedDestination.value ?: return
        val radius = _alertRadiusMeters.value

        viewModelScope.launch(Dispatchers.IO) {
            // Cancel any previously running trip
            userDao.cancelAllActiveTrips()

            // Record new active trip
            val trip = TripEntity(
                destinationName = destination.name,
                destinationLat = destination.latitude,
                destinationLon = destination.longitude,
                transportMode = destination.mode,
                status = "ACTIVE"
            )
            userDao.insertTrip(trip)

            // Start Foreground Service
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                putExtra(TrackingForegroundService.EXTRA_DEST_NAME, destination.name)
                putExtra(TrackingForegroundService.EXTRA_DEST_LAT, destination.latitude)
                putExtra(TrackingForegroundService.EXTRA_DEST_LON, destination.longitude)
                putExtra(TrackingForegroundService.EXTRA_ALERT_RADIUS, radius)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stopTracking(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            userDao.cancelAllActiveTrips()

            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = TrackingForegroundService.ACTION_STOP_ALARM
            }
            context.startService(intent)
        }
    }

    fun savePlace(name: String, lat: Double, lon: Double, mode: String = "TRANSIT") {
        viewModelScope.launch(Dispatchers.IO) {
            val place = SavedPlaceEntity(
                name = name,
                latitude = lat,
                longitude = lon,
                mode = mode
            )
            userDao.insertSavedPlace(place)
        }
    }

    fun deleteSavedPlace(place: SavedPlaceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            userDao.deleteSavedPlace(place)
        }
    }

    fun checkBatteryOptimizationStatus() {
        val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isIgnoringBatteryOptimizations.value = powerManager?.isIgnoringBatteryOptimizations(
            getApplication<Application>().packageName
        ) ?: false
    }
}
