package com.example.spendwise.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Data Access Object for all database operations on the notifications table
@Dao
interface NotificationDao {

    // Insert or replace a notification/SMS entity into the database
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationEntity)

    // Retrieve all records that have not yet been sent to the backend
    @Query("SELECT * FROM notifications WHERE sentToBackend = 0")
    suspend fun getAllUnsent(): List<NotificationEntity>

    // Mark a specific record as successfully sent to the backend
    @Query("UPDATE notifications SET sentToBackend = 1 WHERE id = :id")
    suspend fun markAsSent(id: String)

    // Get the total count of all records in the database
    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getCount(): Int

    // Get the 5 most recent records ordered by timestamp descending
    @Query("SELECT * FROM notifications ORDER BY timestampMs DESC LIMIT 5")
    suspend fun getRecent(): List<NotificationEntity>
}
