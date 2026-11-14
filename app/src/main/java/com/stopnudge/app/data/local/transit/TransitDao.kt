package com.stopnudge.app.data.local.transit

import androidx.room.Dao
import androidx.room.Query

@Dao
interface TransitDao {

    @Query("SELECT * FROM metro_stations ORDER BY name ASC")
    suspend fun getAllMetroStations(): List<MetroStationEntity>

    @Query("SELECT * FROM bus_stops ORDER BY name ASC")
    suspend fun getAllBusStops(): List<BusStopEntity>

    @Query("SELECT * FROM metro_stations WHERE name LIKE '%' || :query || '%' OR name_en LIKE '%' || :query || '%' LIMIT 30")
    suspend fun searchMetroStations(query: String): List<MetroStationEntity>

    @Query("SELECT * FROM bus_stops WHERE name LIKE '%' || :query || '%' OR name_en LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchBusStops(query: String): List<BusStopEntity>

    @Query("SELECT * FROM bus_stops WHERE group_id = :groupId")
    suspend fun getBusStopsByGroup(groupId: String): List<BusStopEntity>

    @Query("SELECT count(*) FROM metro_stations")
    suspend fun getMetroCount(): Int

    @Query("SELECT count(*) FROM bus_stops")
    suspend fun getBusCount(): Int
}
