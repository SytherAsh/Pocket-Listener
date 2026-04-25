package com.example.spendwise

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.spendwise.db.AppDatabase
import com.example.spendwise.db.NotificationEntity
import com.example.spendwise.ui.theme.SpendWiseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// Main dashboard activity — shows permission status, stats, and recent records
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpendWiseTheme {
                SpendWiseDashboard()
            }
        }
    }
}

// Color palette for the premium dark UI
private val DarkBg = Color(0xFF0F0F1A)
private val CardBg = Color(0xFF1A1A2E)
private val AccentGreen = Color(0xFF00E676)
private val AccentRed = Color(0xFFFF5252)
private val AccentBlue = Color(0xFF448AFF)
private val AccentPurple = Color(0xFFB388FF)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)

// Root composable for the entire SpendWise dashboard screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendWiseDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Reactive state for notification listener permission status
    var notifEnabled by remember { mutableStateOf(false) }
    // Reactive state for SMS permission status
    var smsGranted by remember { mutableStateOf(false) }
    // Backend URL text field state
    var backendUrl by remember { mutableStateOf("http://192.168.1.105:8000/api/data") }
    // Total records count from the database
    var totalCount by remember { mutableIntStateOf(0) }
    // Pending (unsent) records count
    var pendingCount by remember { mutableIntStateOf(0) }
    // List of the 5 most recent records for display
    val recentRecords = remember { mutableStateListOf<NotificationEntity>() }
    // Whether initial SMS read has been performed this session
    var smsReadDone by remember { mutableStateOf(false) }
    // Connection test state: null = not tested, true = connected, false = failed
    var connectionStatus by remember { mutableStateOf<Boolean?>(null) }
    // Error message from the last connection test
    var connectionError by remember { mutableStateOf("") }
    // Whether a connection test is currently in progress
    var connectionTesting by remember { mutableStateOf(false) }

    // Launcher for requesting SMS permission at runtime
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        smsGranted = isGranted
        if (isGranted) {
            Toast.makeText(context, "SMS permission granted!", Toast.LENGTH_SHORT).show()
        }
    }

    // Check permissions and refresh stats every 5 seconds using a Handler
    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                // Check if notification listener service is currently enabled
                notifEnabled = isNotificationListenerEnabled(context)
                // Check if READ_SMS permission is granted
                smsGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED

                // Refresh database stats on the IO thread
                scope.launch {
                    val db = AppDatabase.getInstance(context)
                    totalCount = withContext(Dispatchers.IO) { db.dao().getCount() }
                    pendingCount = withContext(Dispatchers.IO) { db.dao().getAllUnsent().size }
                    val recent = withContext(Dispatchers.IO) { db.dao().getRecent() }
                    recentRecords.clear()
                    recentRecords.addAll(recent)
                }

                // Read SMS on first successful permission grant
                if (smsGranted && !smsReadDone) {
                    smsReadDone = true
                    scope.launch(Dispatchers.IO) {
                        SmsReader.readAllSms(context)
                        SmsReader.startSmsObserver(context)
                    }
                }

                // Schedule next refresh in 5 seconds
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(runnable)
        onDispose { handler.removeCallbacksAndMessages(null) }
    }

    Scaffold(
        topBar = {
            // Gradient top app bar with the app name
            TopAppBar(
                title = {
                    Text(
                        "SpendWise",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        },
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Section 1: Notification access status card
            item {
                PermissionCard(
                    label = "Notification Access",
                    isGranted = notifEnabled,
                    buttonText = "Enable Notification Access",
                    onClick = {
                        // Open system settings for notification listener access
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }

            // Section 2: SMS permission status card
            item {
                PermissionCard(
                    label = "SMS Permission",
                    isGranted = smsGranted,
                    buttonText = "Grant SMS Permission",
                    onClick = {
                        // Request READ_SMS permission via the activity result launcher
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                )
            }

            // Section 3: Backend URL configuration card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Backend URL",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Editable text field for the backend API URL
                        OutlinedTextField(
                            value = backendUrl,
                            onValueChange = { backendUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextSecondary,
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = TextSecondary,
                                cursorColor = AccentBlue
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Save button that persists the URL to SharedPreferences
                            Button(
                                onClick = {
                                    ApiSender.setBackendUrl(context, backendUrl)
                                    Toast.makeText(context, "URL saved!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save URL", color = Color.White)
                            }
                            // Test Connection button — pings the backend and shows result
                            Button(
                                onClick = {
                                    connectionTesting = true
                                    connectionStatus = null
                                    connectionError = ""
                                    scope.launch {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                // Try to connect to the backend URL
                                                val testUrl = backendUrl.trimEnd('/').removeSuffix("/api/data")
                                                val url = URL("$testUrl/")
                                                val conn = url.openConnection() as HttpURLConnection
                                                conn.requestMethod = "GET"
                                                conn.connectTimeout = 5000
                                                conn.readTimeout = 5000
                                                val code = conn.responseCode
                                                conn.disconnect()
                                                code
                                            }
                                            connectionStatus = result in 200..299
                                            connectionError = if (connectionStatus == true) "HTTP $result OK" else "HTTP $result"
                                        } catch (e: java.net.ConnectException) {
                                            connectionStatus = false
                                            connectionError = "Connection refused — is test.py running?"
                                        } catch (e: java.net.SocketTimeoutException) {
                                            connectionStatus = false
                                            connectionError = "Timeout — wrong IP or firewall blocking?"
                                        } catch (e: java.net.UnknownHostException) {
                                            connectionStatus = false
                                            connectionError = "Unknown host — check the IP address"
                                        } catch (e: Exception) {
                                            connectionStatus = false
                                            connectionError = e.message ?: "Unknown error"
                                        } finally {
                                            connectionTesting = false
                                        }
                                    }
                                },
                                enabled = !connectionTesting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF424242)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (connectionTesting) "Testing..." else "Test Connection",
                                    color = Color.White
                                )
                            }
                        }
                        // Show connection test result
                        if (connectionStatus != null || connectionTesting) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val bgColor = when {
                                connectionTesting -> Color(0xFF424242)
                                connectionStatus == true -> AccentGreen.copy(alpha = 0.15f)
                                else -> AccentRed.copy(alpha = 0.15f)
                            }
                            val textColor = when {
                                connectionTesting -> TextSecondary
                                connectionStatus == true -> AccentGreen
                                else -> AccentRed
                            }
                            val statusText = when {
                                connectionTesting -> "⏳ Testing connection..."
                                connectionStatus == true -> "✅ CONNECTED"
                                else -> "❌ FAILED"
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgColor)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        statusText,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (connectionError.isNotEmpty()) {
                                        Text(
                                            connectionError,
                                            color = textColor.copy(alpha = 0.8f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Live stats card showing total and pending counts
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Live Stats",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Total captured records stat bubble
                            StatBubble(
                                label = "Total Captured",
                                value = totalCount.toString(),
                                gradient = Brush.linearGradient(
                                    listOf(AccentBlue, AccentPurple)
                                )
                            )
                            // Pending to send records stat bubble
                            StatBubble(
                                label = "Pending Send",
                                value = pendingCount.toString(),
                                gradient = Brush.linearGradient(
                                    listOf(Color(0xFFFF6F00), AccentRed)
                                )
                            )
                        }
                    }
                }
            }

            // Section 5: Retry unsent records button
            item {
                Button(
                    onClick = {
                        scope.launch { ApiSender.retryUnsent(context) }
                        Toast.makeText(context, "Retrying unsent records...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retry Unsent Records", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Section 6: Header for recent records list
            item {
                Text(
                    "Recent Records",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            // Section 7: Display the last 5 records from the database
            if (recentRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "No records yet. Grant permissions and wait for notifications or SMS.",
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(recentRecords) { record ->
                    RecordRow(record)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// Card composable showing a permission status with an action button
@Composable
fun PermissionCard(label: String, isGranted: Boolean, buttonText: String, onClick: () -> Unit) {
    // Animate the status dot color between green (granted) and red (not granted)
    val statusColor by animateColorAsState(
        targetValue = if (isGranted) AccentGreen else AccentRed,
        label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored status indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    if (isGranted) "ACTIVE" else "NOT GRANTED",
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!isGranted) {
                // Show action button only when permission is not yet granted
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(buttonText, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// Circular stat display with a gradient background
@Composable
fun StatBubble(label: String, value: String, gradient: Brush) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

// Single row displaying a recent notification/SMS record
@Composable
fun RecordRow(record: NotificationEntity) {
    // Pick badge color based on source type (notification vs SMS)
    val sourceColor = if (record.source == "notification") AccentPurple else AccentGreen

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Source type badge (NOTIF or SMS)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(sourceColor.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (record.source == "notification") "NOTIF" else "SMS",
                    color = sourceColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Show sender (for SMS) or package name (for notifications)
                Text(
                    record.sender ?: record.packageName ?: "Unknown",
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Show first 50 characters of the body text
                Text(
                    record.body.take(50),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Human-readable timestamp
                Text(
                    record.timestampHuman,
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

// Check if SpendWise's NotificationListenerService is enabled in system settings
fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val cn = ComponentName(context, NotificationService::class.java)
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabledListeners.contains(cn.flattenToString())
}