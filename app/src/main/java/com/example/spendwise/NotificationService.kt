package com.example.spendwise

import android.app.Notification
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.spendwise.db.AppDatabase
import com.example.spendwise.db.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Service that listens for ALL notifications posted on the device
class NotificationService : NotificationListenerService() {

    private val TAG = "SPENDWISE_NOTIF"

    // Called by the system whenever a new notification is posted by any app
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val entity = buildRecord(sbn)
        val db = AppDatabase.getInstance(applicationContext)

        // Save the notification record to Room DB and send to backend
        CoroutineScope(Dispatchers.IO).launch {
            db.dao().insert(entity)
            ApiSender.send(entity, applicationContext)
        }

        Log.d(TAG, "Posted: ${entity.packageName} | ${entity.title} | ${entity.body.take(80)}")
    }

    // Called by the system whenever a notification is removed/dismissed
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Removed: ${sbn.packageName}")
    }

    // Pure function that constructs a NotificationEntity from a StatusBarNotification
    private fun buildRecord(sbn: StatusBarNotification): NotificationEntity {
        val extras = sbn.notification.extras

        // Extract notification text fields safely, handling nulls with ?: ""
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // Get the unique device identifier for this phone
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val postTime = sbn.postTime

        return NotificationEntity(
            id = UUID.randomUUID().toString(),
            source = "notification",
            packageName = sbn.packageName ?: "",
            sender = null,
            title = title,
            body = text,
            bigText = bigText,
            timestampMs = postTime,
            timestampHuman = dateFormat.format(Date(postTime)),
            deviceId = deviceId,
            sentToBackend = false
        )
    }
}
