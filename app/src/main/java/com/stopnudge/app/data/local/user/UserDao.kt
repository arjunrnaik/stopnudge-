package com.stopnudge.app.data.local.user

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    // Saved Places
    @Query("SELECT * FROM saved_places ORDER BY createdAt DESC")
    fun getAllSavedPlaces(): Flow<List<SavedPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPlace(place: SavedPlaceEntity): Long

    @Delete
    suspend fun deleteSavedPlace(place: SavedPlaceEntity)

    // Trips
    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    // Alert Stages
    @Query("SELECT * FROM alert_stages WHERE tripId = :tripId ORDER BY distanceMeters DESC")
    suspend fun getStagesForTrip(tripId: String): List<AlertStageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStages(stages: List<AlertStageEntity>)

    @Update
    suspend fun updateStage(stage: AlertStageEntity)
}
