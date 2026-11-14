package com.stopnudge.app.data.local.transit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MetroStationEntity::class, BusStopEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TransitDatabase : RoomDatabase() {

    abstract fun transitDao(): TransitDao

    companion object {
        @Volatile
        private var INSTANCE: TransitDatabase? = null

        fun getInstance(context: Context): TransitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransitDatabase::class.java,
                    "transit_stops.db"
                )
                    .createFromAsset("databases/transit_stops.db")
                    .fallbackToDestructiveMigration() // Safe only for this read-only static bundled asset
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
