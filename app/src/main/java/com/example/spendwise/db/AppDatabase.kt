package com.example.spendwise.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Room database definition with NotificationEntity as the only table
@Database(entities = [NotificationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Provides access to the NotificationDao for all database operations
    abstract fun dao(): NotificationDao

    companion object {
        // Volatile instance ensures visibility across threads
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Thread-safe singleton getter — creates the database only once
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spendwise_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
