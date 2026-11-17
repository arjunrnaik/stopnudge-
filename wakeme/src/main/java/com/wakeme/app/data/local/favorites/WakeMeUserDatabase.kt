package com.wakeme.app.data.local.favorites

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoritePlaceEntity::class,
        RecentTripEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WakeMeUserDatabase : RoomDatabase() {

    abstract fun favoritePlaceDao(): FavoritePlaceDao
    abstract fun recentTripDao(): RecentTripDao

    companion object {
        private const val DB_NAME = "wakeme_user.db"

        @Volatile
        private var INSTANCE: WakeMeUserDatabase? = null

        fun getInstance(context: Context): WakeMeUserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WakeMeUserDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
