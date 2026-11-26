package com.wakeme.app.data.local.favorites

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoritePlaceEntity::class,
        RecentTripEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class WakeMeUserDatabase : RoomDatabase() {

    abstract fun favoritePlaceDao(): FavoritePlaceDao
    abstract fun recentTripDao(): RecentTripDao

    companion object {
        private const val DB_NAME = "wakeme_user.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recent_trips (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        destinationName TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        vehicleMode TEXT NOT NULL,
                        radiusMeters REAL NOT NULL,
                        alertMode TEXT NOT NULL,
                        audioRouting TEXT NOT NULL,
                        reminderNote TEXT NOT NULL,
                        timestampMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: WakeMeUserDatabase? = null

        fun getInstance(context: Context): WakeMeUserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WakeMeUserDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
