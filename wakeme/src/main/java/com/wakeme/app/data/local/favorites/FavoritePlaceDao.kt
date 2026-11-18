package com.wakeme.app.data.local.favorites

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritePlaceDao {

    @Query("SELECT * FROM favorite_places ORDER BY id ASC")
    fun getAllFavorites(): Flow<List<FavoritePlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(place: FavoritePlaceEntity): Long

    @Update
    suspend fun updateFavorite(place: FavoritePlaceEntity)

    @Delete
    suspend fun deleteFavorite(place: FavoritePlaceEntity)

    @Query("DELETE FROM favorite_places WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT count(*) FROM favorite_places")
    suspend fun count(): Int
}
