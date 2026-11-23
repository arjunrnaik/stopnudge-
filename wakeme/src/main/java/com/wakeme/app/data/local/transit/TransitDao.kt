package com.wakeme.app.data.local.transit

import androidx.room.Dao
import androidx.room.Query

@Dao
interface TransitDao {

    @Query("SELECT * FROM metro_stations ORDER BY name ASC")
    suspend fun getAllMetroStations(): List<MetroStationEntity>

    @Query("SELECT * FROM bus_stops ORDER BY name ASC")
    suspend fun getAllBusStops(): List<BusStopEntity>

    @Query("SELECT * FROM bus_stops ORDER BY name ASC LIMIT 100")
    suspend fun getInitialBusStops(): List<BusStopEntity>

    @Query("SELECT * FROM bus_stops WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon LIMIT :limit")
    suspend fun getBusStopsInBoundingBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, limit: Int = 500): List<BusStopEntity>

    @Query("SELECT * FROM bus_stops WHERE latitude BETWEEN (:lat - 0.05) AND (:lat + 0.05) AND longitude BETWEEN (:lon - 0.05) AND (:lon + 0.05) ORDER BY ((latitude - :lat) * (latitude - :lat) + (longitude - :lon) * (longitude - :lon)) ASC LIMIT 1")
    suspend fun findNearestBusStop(lat: Double, lon: Double): BusStopEntity?

    @Query("SELECT * FROM metro_stations WHERE name LIKE '%' || :query || '%' OR name_en LIKE '%' || :query || '%' OR (name_kn IS NOT NULL AND name_kn LIKE '%' || :query || '%') ORDER BY name ASC LIMIT 50")
    suspend fun searchMetroStations(query: String): List<MetroStationEntity>

    @Query("SELECT * FROM bus_stops WHERE name LIKE '%' || :query || '%' OR name_en LIKE '%' || :query || '%' OR (name_kn IS NOT NULL AND name_kn LIKE '%' || :query || '%') ORDER BY name ASC LIMIT 50")
    suspend fun searchBusStops(query: String): List<BusStopEntity>

    @Query("SELECT * FROM bus_stops WHERE group_id = :groupId")
    suspend fun getBusStopsByGroup(groupId: String): List<BusStopEntity>

    @Query("SELECT count(*) FROM metro_stations")
    suspend fun getMetroCount(): Int

    @Query("SELECT count(*) FROM bus_stops")
    suspend fun getBusCount(): Int
}
