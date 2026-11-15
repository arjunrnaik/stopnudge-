package com.stopnudge.app.data.local.transit

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bus_stops",
    indices = [
        Index(value = ["latitude", "longitude"], name = "idx_bus_coords"),
        Index(value = ["name_en"], name = "idx_bus_name"),
        Index(value = ["group_id"], name = "idx_bus_group")
    ]
)
data class BusStopEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "name_en")
    val nameEn: String,
    @ColumnInfo(name = "name_kn")
    val nameKn: String?,
    val latitude: Double,
    val longitude: Double,
    val mode: String,
    @ColumnInfo(name = "metro_lines")
    val metroLines: String?,
    val operator: String?,
    val ref: String?,
    @ColumnInfo(name = "group_id")
    val groupId: String?,
    @ColumnInfo(name = "geometry_kind")
    val geometryKind: String,
    val flags: String,
    val source: String,
    @ColumnInfo(name = "osm_timestamp")
    val osmTimestamp: String?,
    @ColumnInfo(name = "last_checked_date")
    val lastCheckedDate: String,
    val verified: Int
)
