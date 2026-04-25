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

// Handles sending notification/SMS records to the Python backend as JSON
object ApiSender {

    private const val TAG = "SPENDWISE_API"
    private const val PREFS_NAME = "spendwise_prefs"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val DEFAULT_URL = "http://10.0.2.2:5000/api/data"

    // Convert a NotificationEntity to a JSON string matching the required schema
    private fun entityToJson(entity: NotificationEntity): String {
        val json = JSONObject()
        json.put("id", entity.id)
        json.put("source", entity.source)
        json.put("package_name", entity.packageName ?: JSONObject.NULL)
        json.put("sender", entity.sender ?: JSONObject.NULL)
        json.put("title", entity.title ?: JSONObject.NULL)
        json.put("body", entity.body)
        json.put("big_text", entity.bigText ?: JSONObject.NULL)
        json.put("timestamp_ms", entity.timestampMs)
        json.put("timestamp_human", entity.timestampHuman)
        json.put("device_id", entity.deviceId)
        json.put("sent_to_backend", entity.sentToBackend)
        return json.toString()
    }

    // Get the backend URL from SharedPreferences, falling back to default
    private fun getBackendUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BACKEND_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    // Save a custom backend URL to SharedPreferences
    fun setBackendUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKEND_URL, url).apply()
        Log.d(TAG, "Backend URL updated to: $url")
    }

    // POST a single entity as JSON to the backend on the IO dispatcher
    suspend fun send(entity: NotificationEntity, context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val urlString = getBackendUrl(context)
                val jsonString = entityToJson(entity)
                val url = URL(urlString)
                // Open HTTP connection and configure for JSON POST
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                // Write the JSON body to the request output stream
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonString)
                writer.flush()
                writer.close()

                // Check response code and mark as sent on success
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    AppDatabase.getInstance(context).dao().markAsSent(entity.id)
                    Log.d(TAG, "Sent successfully: ${entity.id}")
                } else {
                    Log.e(TAG, "Server returned HTTP $responseCode for ${entity.id}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Leave sentToBackend = false so it can be retried later
                Log.e(TAG, "Failed to send ${entity.id}: ${e.message}")
            }
        }
    }

    // Retry sending all records that previously failed to reach the backend
    suspend fun retryUnsent(context: Context) {
        withContext(Dispatchers.IO) {
            val unsent = AppDatabase.getInstance(context).dao().getAllUnsent()
            Log.d(TAG, "Retrying ${unsent.size} unsent records")
            for (entity in unsent) {
                send(entity, context)
            }
        }
    }
}
