package com.stopnudge.app.data.local.user

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val mode: String = "TRANSIT",
    val address: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
