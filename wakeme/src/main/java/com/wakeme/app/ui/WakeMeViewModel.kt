package com.wakeme.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wakeme.app.data.local.transit.BusStopEntity
import com.wakeme.app.data.local.transit.MetroStationEntity
import com.wakeme.app.data.local.transit.TransitDatabase
import com.wakeme.app.model.AlertMode
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.model.DestinationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class WakeMeViewModel(application: Application) : AndroidViewModel(application) {

    private val transitDao = TransitDatabase.getInstance(application).transitDao()

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

    private val _reminderNote = MutableStateFlow("")
    val reminderNote: StateFlow<String> = _reminderNote.asStateFlow()

    private val _showTransitOverlay = MutableStateFlow(false)
    val showTransitOverlay: StateFlow<Boolean> = _showTransitOverlay.asStateFlow()

    private val _metroStations = MutableStateFlow<List<MetroStationEntity>>(emptyList())
    val metroStations: StateFlow<List<MetroStationEntity>> = _metroStations.asStateFlow()

    private val _busStops = MutableStateFlow<List<BusStopEntity>>(emptyList())
    val busStops: StateFlow<List<BusStopEntity>> = _busStops.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    init {
        // Preload metro stations in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val metros = transitDao.getAllMetroStations()
                _metroStations.value = metros
            } catch (_: Exception) {
            }
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

    fun setReminderNote(note: String) {
        _reminderNote.value = note
    }

    fun toggleTransitOverlay() {
        val next = !_showTransitOverlay.value
        _showTransitOverlay.value = next
        if (next && _busStops.value.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val initialBuses = transitDao.getInitialBusStops()
                    _busStops.value = initialBuses
                } catch (_: Exception) {
                }
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
            alertMode = _alertMode.value,
            audioRouting = _audioRouting.value,
            reminderNote = _reminderNote.value
        )
    }

    fun setTrackingActive(active: Boolean) {
        _isTrackingActive.value = active
    }
}
