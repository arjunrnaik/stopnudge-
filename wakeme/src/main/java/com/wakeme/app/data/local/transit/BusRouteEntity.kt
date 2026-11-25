package com.wakeme.app.data.local.transit

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bus_routes",
    indices = [
        Index(value = ["route_no"], name = "idx_bus_route_no")
    ]
)
data class BusRouteEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "route_no")
    val routeNo: String,
    val origin: String,
    val destination: String,
    @ColumnInfo(name = "stop_ids")
    val stopIds: String,
    @ColumnInfo(name = "stop_count")
    val stopCount: Int
)
