package com.wakeme.app.data.local.favorites

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentTripDao {

    @Query("SELECT * FROM recent_trips ORDER BY timestamp DESC LIMIT 8")
    fun getRecentTrips(): Flow<List<RecentTripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentTrip(trip: RecentTripEntity): Long

    @Query("DELETE FROM recent_trips WHERE id NOT IN (SELECT id FROM recent_trips ORDER BY timestamp DESC LIMIT 10)")
    suspend fun trimOldTrips()

    @Query("DELETE FROM recent_trips")
    suspend fun clearRecentTrips()
}
