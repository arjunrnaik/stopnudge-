package com.stopnudge.app.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlin.math.*

class LocationTrackingManager(
    private val context: Context,
    private val onLocationUpdate: (Location, Double) -> Unit
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var targetLat: Double = 0.0
    private var targetLon: Double = 0.0
    private var isTracking: Boolean = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val distanceMeters = calculateDistance(
                location.latitude,
                location.longitude,
                targetLat,
                targetLon
            )
            onLocationUpdate(location, distanceMeters)
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(destLat: Double, destLon: Double) {
        targetLat = destLat
        targetLon = destLon
        isTracking = true

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L // 5 seconds interval
        ).apply {
            setMinUpdateIntervalMillis(3000L) // Fastest interval: 3 seconds
            setMinUpdateDistanceMeters(10f) // Minimum 10 meters displacement
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
