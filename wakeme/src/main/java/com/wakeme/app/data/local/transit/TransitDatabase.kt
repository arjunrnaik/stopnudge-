package com.wakeme.app.data.local.transit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MetroStationEntity::class, BusStopEntity::class, BusRouteEntity::class],
    version = 9,
    exportSchema = true
)
abstract class TransitDatabase : RoomDatabase() {

    abstract fun transitDao(): TransitDao

    companion object {
        private const val DB_NAME = "wakeme_transit_stops_v9.db"
        private const val ASSET_PATH = "databases/transit_stops.db"

        @Volatile
        private var INSTANCE: TransitDatabase? = null

        fun getInstance(context: Context): TransitDatabase {
            return INSTANCE ?: synchronized(this) {
                // Clean up legacy v1 through v8 db files so v9 BMTC + Metro DB loads cleanly
                for (legacyName in listOf(
                    "transit_stops.db",
                    "transit_stops_v2.db",
                    "wakeme_transit_stops_v3.db",
                    "wakeme_transit_stops_v4.db",
                    "wakeme_transit_stops_v5.db",
                    "wakeme_transit_stops_v6.db",
                    "wakeme_transit_stops_v7.db",
                    "wakeme_transit_stops_v8.db"
                )) {
                    val oldDb = context.applicationContext.getDatabasePath(legacyName)
                    if (oldDb.exists()) {
                        oldDb.delete()
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransitDatabase::class.java,
                    DB_NAME
                )
                    .createFromAsset(ASSET_PATH)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
