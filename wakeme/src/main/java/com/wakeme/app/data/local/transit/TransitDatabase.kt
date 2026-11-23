package com.wakeme.app.data.local.transit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MetroStationEntity::class, BusStopEntity::class],
    version = 6,
    exportSchema = false
)
abstract class TransitDatabase : RoomDatabase() {

    abstract fun transitDao(): TransitDao

    companion object {
        private const val DB_NAME = "wakeme_transit_stops_v6.db"
        private const val ASSET_PATH = "databases/transit_stops.db"

        @Volatile
        private var INSTANCE: TransitDatabase? = null

        fun getInstance(context: Context): TransitDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
                if (dbFile.exists() && dbFile.length() < 500_000L) {
                    dbFile.delete()
                }

                // Clean up legacy v1, v2, v3, v4, and v5 db files if present to force-load the 10,156 stops
                for (legacyName in listOf("transit_stops.db", "transit_stops_v2.db", "wakeme_transit_stops_v3.db", "wakeme_transit_stops_v4.db", "wakeme_transit_stops_v5.db")) {
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
