package com.wakeme.app.data.local.favorites

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists recent commuter trip destinations for 1-tap re-routing.
 */
@Entity(tableName = "recent_trips")
data class RecentTripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val destinationName: String,
    val latitude: Double,
    val longitude: Double,
    val vehicleMode: String, // "CAR", "BUS"
    val radiusMeters: Float = 500f,
    val alertMode: String = "WAKEUP_ALARM",
    val audioRouting: String = "BOTH",
    val reminderNote: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
