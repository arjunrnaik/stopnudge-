package com.wakeme.app.data.local.transit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MetroStationEntity::class, BusStopEntity::class],
    version = 3,
    exportSchema = false
)
abstract class TransitDatabase : RoomDatabase() {

    abstract fun transitDao(): TransitDao

    companion object {
        private const val DB_NAME = "wakeme_transit_stops_v3.db"
        private const val ASSET_PATH = "databases/transit_stops.db"

        @Volatile
        private var INSTANCE: TransitDatabase? = null

        fun getInstance(context: Context): TransitDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
                if (dbFile.exists() && dbFile.length() < 500_000L) {
                    dbFile.delete()
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
