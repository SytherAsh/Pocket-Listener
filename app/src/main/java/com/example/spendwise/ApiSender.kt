package com.example.spendwise

import android.content.Context
import android.util.Log
import com.example.spendwise.db.AppDatabase
import com.example.spendwise.db.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL


// Handles sending SMS records to the Python backend as JSON
object ApiSender {

    private const val TAG = "SPENDWISE_API"
    private const val PREFS_NAME = "spendwise_prefs"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val DEFAULT_URL = "http://10.0.2.2:8000/api/data"

    // Convert a NotificationEntity to a JSON object (SMS focus)
    private fun entityToJsonObject(entity: NotificationEntity): JSONObject {
        val json = JSONObject()
        json.put("id", entity.id)
        json.put("source", "sms")
        json.put("sender", entity.sender ?: JSONObject.NULL)
        json.put("body", entity.body)
        json.put("timestamp_ms", entity.timestampMs)
        json.put("timestamp_human", entity.timestampHuman)
        json.put("device_id", entity.deviceId)
        json.put("sent_to_backend", entity.sentToBackend)
        return json
    }

    private fun getBackendUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BACKEND_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun setBackendUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKEND_URL, url).apply()
    }

    // POST a single entity
    suspend fun send(entity: NotificationEntity, context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val urlString = getBackendUrl(context)
                val body = entityToJsonObject(entity).toString()
                postJson(urlString, body)
                AppDatabase.getInstance(context).dao().markAsSent(entity.id)
                Log.d(TAG, "Sent single SMS: ${entity.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send single: ${e.message}")
            }
        }
    }

    // NEW: Bulk send for high-speed sync
    suspend fun retryUnsent(context: Context) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val unsent = db.dao().getAllUnsent()
            if (unsent.isEmpty()) return@withContext

            val baseUrl = getBackendUrl(context).trimEnd('/').removeSuffix("/api/data")
            val bulkUrl = "$baseUrl/api/data/bulk"
            
            // Send in batches of 100
            unsent.chunked(100).forEach { batch ->
                try {
                    val array = org.json.JSONArray()
                    batch.forEach { array.put(entityToJsonObject(it)) }
                    
                    postJson(bulkUrl, array.toString())
                    
                    // Mark all in batch as sent
                    batch.forEach { db.dao().markAsSent(it.id) }
                    Log.d(TAG, "Synced batch of ${batch.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Bulk sync failed for batch: ${e.message}")
                }
            }
        }
    }

    private fun postJson(urlString: String, jsonBody: String) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(jsonBody)
        writer.flush()
        writer.close()

        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) throw Exception("HTTP $code")
    }
}

