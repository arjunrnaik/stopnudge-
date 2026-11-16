package com.wakeme.app.data.local.favorites

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_places")
data class FavoritePlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String, // "HOME", "WORK", "TRANSIT", "CUSTOM"
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 500f,
    val alertMode: String = "WAKEUP_ALARM",
    val audioRouting: String = "BOTH",
    val reminderNote: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
