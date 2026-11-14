package com.stopnudge.app.data.local.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val destinationName: String,
    val destinationLat: Double,
    val destinationLon: Double,
    val transportMode: String = "TRANSIT",
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, CANCELLED, INTERRUPTED
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val distanceCoveredMeters: Double = 0.0,
    val notes: String? = null
)
