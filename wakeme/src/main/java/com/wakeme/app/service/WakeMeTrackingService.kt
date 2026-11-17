package com.wakeme.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import com.google.android.gms.location.*
import com.wakeme.app.MainActivity
import com.wakeme.app.WakeMeApp
import com.wakeme.app.alarm.AlarmDismissActivity
import com.wakeme.app.alarm.WakeMeAlarmController
import com.wakeme.app.data.RoadRoutingRepository
import com.wakeme.app.model.AlertMode
import com.wakeme.app.model.AudioRouting
import com.wakeme.app.model.DestinationConfig
import com.wakeme.app.model.VehicleMode

class WakeMeTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private var destinationConfig: DestinationConfig? = null
    private var lastDistanceMeters: Float = Float.MAX_VALUE
    private var hasAlertFired = false
    private var lastRouteFetchTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_STOP_TRACKING -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_TRACKING -> {
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Destination"
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                val radius = intent.getFloatExtra(EXTRA_RADIUS, 500f)
                val vehicleMode = intent.getStringExtra(EXTRA_VEHICLE_MODE)?.let { VehicleMode.valueOf(it) } ?: VehicleMode.BUS
                val mode = intent.getStringExtra(EXTRA_MODE)?.let { AlertMode.valueOf(it) } ?: AlertMode.WAKEUP_ALARM
                val routing = intent.getStringExtra(EXTRA_ROUTING)?.let { AudioRouting.valueOf(it) } ?: AudioRouting.BOTH
                val note = intent.getStringExtra(EXTRA_NOTE) ?: ""

                destinationConfig = DestinationConfig(
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    radiusMeters = radius,
                    vehicleMode = vehicleMode,
                    alertMode = mode,
                    audioRouting = routing,
                    reminderNote = note
                )
                hasAlertFired = false
                isTracking.value = true
                activeConfig.value = destinationConfig
                initialDistanceMeters.value = null
                currentSpeedKmh.value = null
                estimatedEtaMinutes.value = null
                routePolyline.value = emptyList()
                isRoadRouteActive.value = false
                currentBearing.value = null
                currentLocationPoint.value = null
                lastRouteFetchTime = 0L

                startForegroundNotification(name, "Initiating hardware GPS tracking...")
                startLocationUpdates()
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification(title: String, statusText: String) {
        val notification = buildTrackingNotification(title, statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildTrackingNotification(title: String, statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeMeTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WakeMeApp.TRACKING_CHANNEL_ID)
            .setContentTitle("WakeMe: $title")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val config = destinationConfig ?: return
        val notification = buildTrackingNotification(config.name, statusText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
            .setMinUpdateIntervalMillis(1500L)
            .setMinUpdateDistanceMeters(3f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onNewLocation(location)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (_: Exception) {}
    }

    private fun onNewLocation(currentLocation: Location) {
        val config = destinationConfig ?: return

        // 1. Hardware straight-line distance for micro-geofence trigger
        val straightDist = haversineDistanceMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            config.latitude,
            config.longitude
        ).toFloat()

        straightLineDistanceMeters.value = straightDist
        lastDistanceMeters = straightDist

        // 2. Telemetry: Location, Bearing & Speed
        currentLocationPoint.value = GeoPoint(currentLocation.latitude, currentLocation.longitude)
        if (currentLocation.hasBearing()) {
            currentBearing.value = currentLocation.bearing
        }

        val speedMps = if (currentLocation.hasSpeed()) currentLocation.speed else 0f
        val speedKmh = speedMps * 3.6f
        currentSpeedKmh.value = speedKmh

        // 3. Road Distance & Route Geometry Query (OSRM with offline fallback)
        val now = System.currentTimeMillis()
        if (now - lastRouteFetchTime > 15_000L || liveDistanceMeters.value == null) {
            lastRouteFetchTime = now
            serviceScope.launch {
                val route = RoadRoutingRepository.getRoute(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    config.latitude,
                    config.longitude
                )
                liveDistanceMeters.value = route.distanceMeters
                routePolyline.value = route.points
                isRoadRouteActive.value = route.isRoadRoute

                if (initialDistanceMeters.value == null || initialDistanceMeters.value == 0f) {
                    initialDistanceMeters.value = route.distanceMeters
                }

                if (speedMps > 1.0f) {
                    val etaSec = (route.distanceMeters / speedMps).toInt()
                    val etaMin = (etaSec / 60).coerceAtLeast(1)
                    estimatedEtaMinutes.value = etaMin
                }
            }
        } else {
            // Extrapolate distance decrease between routing fetches
            if (liveDistanceMeters.value == null) {
                liveDistanceMeters.value = straightDist * 1.28f
            }
            if (speedMps > 1.0f && liveDistanceMeters.value != null) {
                val etaSec = (liveDistanceMeters.value!! / speedMps).toInt()
                val etaMin = (etaSec / 60).coerceAtLeast(1)
                estimatedEtaMinutes.value = etaMin
            }
        }

        // 4. Geofence Trigger Condition
        if (straightDist <= config.radiusMeters) {
            if (!hasAlertFired) {
                hasAlertFired = true
                triggerArrivalAlert(config, straightDist)
            }
        } else {
            val dist = liveDistanceMeters.value ?: (straightDist * 1.28f)
            val distText = if (dist >= 1000f) {
                String.format("%.1f km remaining", dist / 1000f)
            } else {
                "${dist.toInt()}m remaining"
            }
            val etaText = estimatedEtaMinutes.value?.let { " • ~$it min" } ?: ""
            updateNotification("$distText$etaText")
        }
    }

    private fun triggerArrivalAlert(config: DestinationConfig, finalDistance: Float) {
        // 1. Trigger audio & vibration engine
        WakeMeAlarmController.triggerAlert(applicationContext, config)

        // 2. Alert mode dispatch
        if (config.alertMode == AlertMode.WAKEUP_ALARM) {
            // Launch full-screen wake-lock activity over lock screen
            val dismissIntent = Intent(this, AlarmDismissActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AlarmDismissActivity.EXTRA_DESTINATION_NAME, config.name)
                putExtra(AlarmDismissActivity.EXTRA_REMINDER_NOTE, config.reminderNote)
                putExtra(AlarmDismissActivity.EXTRA_RADIUS_METERS, config.radiusMeters)
            }
            startActivity(dismissIntent)
        } else {
            // Gentle Reminder: Post heads-up notification
            postGentleReminderNotification(config)
        }

        updateNotification("Arrived at destination (${finalDistance.toInt()}m)")
    }

    private fun postGentleReminderNotification(config: DestinationConfig) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, WakeMeApp.GENTLE_CHANNEL_ID)
            .setContentTitle("Arrived at ${config.name}")
            .setContentText(if (config.reminderNote.isNotEmpty()) config.reminderNote else "You have reached your destination geofence.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ARRIVAL_ID, notif)
    }

    private fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        WakeMeAlarmController.cancelSnooze()
        WakeMeAlarmController.stopAlert(this)
        hasAlertFired = false
        isTracking.value = false
        liveDistanceMeters.value = null
        straightLineDistanceMeters.value = null
        currentSpeedKmh.value = null
        estimatedEtaMinutes.value = null
        initialDistanceMeters.value = null
        routePolyline.value = emptyList()
        isRoadRouteActive.value = false
        currentBearing.value = null
        currentLocationPoint.value = null
        activeConfig.value = null
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_ARRIVAL_ID = 2002

        const val ACTION_START_TRACKING = "com.wakeme.app.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.wakeme.app.action.STOP_TRACKING"

        const val EXTRA_NAME = "extra_name"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_RADIUS = "extra_radius"
        const val EXTRA_VEHICLE_MODE = "extra_vehicle_mode"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_ROUTING = "extra_routing"
        const val EXTRA_NOTE = "extra_note"

        val liveDistanceMeters = MutableStateFlow<Float?>(null) // Real Road Driving Distance
        val straightLineDistanceMeters = MutableStateFlow<Float?>(null) // Geodesic straight-line
        val currentSpeedKmh = MutableStateFlow<Float?>(null)
        val estimatedEtaMinutes = MutableStateFlow<Int?>(null)
        val initialDistanceMeters = MutableStateFlow<Float?>(null)
        val routePolyline = MutableStateFlow<List<GeoPoint>>(emptyList())
        val isRoadRouteActive = MutableStateFlow(false)
        val currentBearing = MutableStateFlow<Float?>(null)
        val currentLocationPoint = MutableStateFlow<GeoPoint?>(null)
        val isTracking = MutableStateFlow(false)
        val activeConfig = MutableStateFlow<DestinationConfig?>(null)

        fun start(context: Context, config: DestinationConfig) {
            val intent = Intent(context, WakeMeTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_NAME, config.name)
                putExtra(EXTRA_LATITUDE, config.latitude)
                putExtra(EXTRA_LONGITUDE, config.longitude)
                putExtra(EXTRA_RADIUS, config.radiusMeters)
                putExtra(EXTRA_VEHICLE_MODE, config.vehicleMode.name)
                putExtra(EXTRA_MODE, config.alertMode.name)
                putExtra(EXTRA_ROUTING, config.audioRouting.name)
                putExtra(EXTRA_NOTE, config.reminderNote)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeMeTrackingService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }

        /**
         * Pure offline CPU Haversine geodesic calculation.
         * Requires 0 network bytes.
         */
        fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        }
    }
}
