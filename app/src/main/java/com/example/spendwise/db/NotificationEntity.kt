package com.example.spendwise.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// Room entity representing a captured notification or SMS record
@Entity(tableName = "notifications")
data class NotificationEntity(
    // Unique UUID string for each record
    @PrimaryKey val id: String,
    // "notification" or "sms" — identifies the data source
    val source: String,
    // Package name of the app that sent the notification, null for SMS
    val packageName: String?,
    // Sender address for SMS or null for notifications
    val sender: String?,
    // Notification title or null for SMS
    val title: String?,
    // Full body text of the notification or SMS
    val body: String,
    // Expanded big-text content from notification or null
    val bigText: String?,
    // Unix timestamp in milliseconds when the record was created
    val timestampMs: Long,
    // Human-readable timestamp string like "2025-04-25 14:30:00"
    val timestampHuman: String,
    // Unique device identifier from Settings.Secure.ANDROID_ID
    val deviceId: String,
    // Whether this record has been successfully sent to the backend
    val sentToBackend: Boolean = false
)
