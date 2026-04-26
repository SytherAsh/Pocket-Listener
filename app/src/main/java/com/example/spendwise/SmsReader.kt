package com.example.spendwise

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
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

// Reads all SMS messages from the phone and watches for new ones in real time
object SmsReader {

    private const val TAG = "SPENDWISE_SMS"
    private const val PREFS_NAME = "spendwise_prefs"
    private const val KEY_LAST_SMS_TIMESTAMP = "last_sms_timestamp"

    // Bank and payment sender keywords used to filter financial SMS
    private val SENDER_KEYWORDS = listOf(
        "HDFCBK", "SBIINB", "ICICIB", "AXISBK", "PAYTM", "GPAY", "PHONEPE",
        "KOTAKB", "YESBNK", "INDBNK", "BOIIND", "PNBSMS"
    )

    // Transaction-related body keywords used to identify financial SMS
    private val BODY_KEYWORDS = listOf(
        "debited", "credited", "UPI", "IMPS", "NEFT", "RTGS",
        "Rs.", "INR", "₹", "transaction", "payment"
    )

    // Read ALL SMS messages and save only NEW ones to Room DB
    fun readAllSms(context: Context): List<NotificationEntity> {
        val results = mutableListOf<NotificationEntity>()
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val db = AppDatabase.getInstance(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Only read messages newer than what we already have
        val lastStoredTimestamp = prefs.getLong(KEY_LAST_SMS_TIMESTAMP, 0L)

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(lastStoredTimestamp.toString()),
            "${Telephony.Sms.DATE} ASC"
        )

        var maxTimestamp = lastStoredTimestamp

        cursor?.use {
            val idxAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val idxBody = it.getColumnIndex(Telephony.Sms.BODY)
            val idxDate = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val address = if (idxAddress >= 0) it.getString(idxAddress) else null
                val body = if (idxBody >= 0) (it.getString(idxBody) ?: "") else ""
                val dateMs = if (idxDate >= 0) it.getLong(idxDate) else 0L

                if (dateMs > maxTimestamp) maxTimestamp = dateMs

                val entity = NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    source = "sms",
                    packageName = null,
                    sender = address,
                    title = null,
                    body = body,
                    bigText = null,
                    timestampMs = dateMs,
                    timestampHuman = dateFormat.format(Date(dateMs)),
                    deviceId = deviceId,
                    sentToBackend = false
                )
                results.add(entity)
                
                // Save to Room DB synchronously or in a controlled batch to avoid lag
                db.dao().insert(entity)
            }
        }
        
        if (maxTimestamp > lastStoredTimestamp) {
            prefs.edit().putLong(KEY_LAST_SMS_TIMESTAMP, maxTimestamp).apply()
        }

        Log.d(TAG, "Read and saved ${results.size} new SMS")
        return results
    }

    // Register a ContentObserver to watch for new SMS messages in real time
    fun startSmsObserver(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Read new SMS messages that arrived after the last known timestamp
                CoroutineScope(Dispatchers.IO).launch {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastTimestamp = prefs.getLong(KEY_LAST_SMS_TIMESTAMP, 0L)
                    val allSms = readAllSms(context)

                    // Filter to only new records based on timestamp comparison
                    val newSms = allSms.filter { it.timestampMs > lastTimestamp }
                    if (newSms.isNotEmpty()) {
                        Log.d(TAG, "New SMS detected: ${newSms.size}")
                        // Update the last known timestamp to prevent re-processing
                        val maxTimestamp = newSms.maxOf { it.timestampMs }
                        prefs.edit().putLong(KEY_LAST_SMS_TIMESTAMP, maxTimestamp).apply()

                        // Send each new SMS to the backend
                        for (sms in newSms) {
                            ApiSender.send(sms, context)
                        }
                    }
                }
            }
        }

        // Register the observer on the SMS content URI for real-time updates
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            observer
        )
        Log.d(TAG, "SMS observer registered")
    }

    // Filter SMS list to keep only financial transaction messages
    fun filterTransactionSms(list: List<NotificationEntity>): List<NotificationEntity> {
        return list.filter { entity ->
            val senderMatch = SENDER_KEYWORDS.any { keyword ->
                // Case-insensitive check if sender contains a known bank/payment keyword
                entity.sender?.contains(keyword, ignoreCase = true) == true
            }
            val bodyMatch = BODY_KEYWORDS.any { keyword ->
                // Case-insensitive check if body contains a transaction-related keyword
                entity.body.contains(keyword, ignoreCase = true)
            }
            senderMatch || bodyMatch
        }
    }
}
