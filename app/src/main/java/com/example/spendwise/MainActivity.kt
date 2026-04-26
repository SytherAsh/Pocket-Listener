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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

// Color palette for the professional premium UI
private val DarkBg = Color(0xFF0A0A12)
private val SurfaceBg = Color(0xFF161626)
private val AccentGreen = Color(0xFF00C853)
private val AccentRed = Color(0xFFEF5350)
private val AccentBlue = Color(0xFF2979FF)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendWiseDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var smsGranted by remember { mutableStateOf(false) }
    var backendUrl by remember { mutableStateOf("http://192.168.1.105:8000/api/data") }
    var totalCount by remember { mutableIntStateOf(0) }
    var pendingCount by remember { mutableIntStateOf(0) }
    val recentRecords = remember { mutableStateListOf<NotificationEntity>() }
    var connectionStatus by remember { mutableStateOf<Boolean?>(null) }
    var isSystemExpanded by remember { mutableStateOf(false) }
    var smsReadDone by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        smsGranted = isGranted
    }

    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                
                scope.launch {
                    val db = AppDatabase.getInstance(context)
                    totalCount = withContext(Dispatchers.IO) { db.dao().getCount() }
                    pendingCount = withContext(Dispatchers.IO) { db.dao().getAllUnsent().size }
                    val recent = withContext(Dispatchers.IO) { db.dao().getRecent() }
                    recentRecords.clear()
                    recentRecords.addAll(recent)
                }

                if (smsGranted && !smsReadDone) {
                    smsReadDone = true
                    scope.launch(Dispatchers.IO) {
                        SmsReader.readAllSms(context)
                        SmsReader.startSmsObserver(context)
                    }
                }

                // Auto-check connection in background
                scope.launch(Dispatchers.IO) {
                    try {
                        val url = URL(backendUrl.trimEnd('/').removeSuffix("/api/data"))
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 3000
                        val code = conn.responseCode
                        connectionStatus = (code == 200)
                        conn.disconnect()
                    } catch (e: Exception) {
                        connectionStatus = false
                    }
                }
                handler.postDelayed(this, 10000)
            }
        }
        handler.post(runnable)
        onDispose { handler.removeCallbacksAndMessages(null) }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text("SpendWise", fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                },
                actions = {
                    StatusIcon(label = "SMS", active = smsGranted)
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusIcon(label = "API", active = connectionStatus == true, isPending = connectionStatus == null)
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            // HERO CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg)
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF2E2E4D), SurfaceBg))
                    )) {
                        Column(modifier = Modifier.padding(24.dp).align(Alignment.CenterStart)) {
                            Text("Total Records Captured", color = TextSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(totalCount.toString(), color = TextPrimary, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (pendingCount > 0) AccentRed else AccentGreen))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (pendingCount > 0) "$pendingCount pending sync" else "All synced", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                Text("Recent Activity", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (recentRecords.isEmpty()) {
                item {
                    Text("Waiting for data capture...", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                }
            }

            items(recentRecords) { record ->
                TransactionItem(record)
            }

            // SYSTEM SETTINGS
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceBg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("System Settings", color = TextSecondary, fontSize = 14.sp)
                        Button(
                            onClick = { isSystemExpanded = !isSystemExpanded },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (isSystemExpanded) "Hide" else "Show", color = AccentBlue)
                        }
                    }
                    
                    if (isSystemExpanded) {
                        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!smsGranted) {
                                SystemActionChip("Grant SMS Permission") { 
                                    smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                }
                            }
                            
                            OutlinedTextField(
                                value = backendUrl,
                                onValueChange = { backendUrl = it },
                                label = { Text("Backend URL") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    unfocusedBorderColor = Color.DarkGray
                                )
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { 
                                    ApiSender.setBackendUrl(context, backendUrl) 
                                    Toast.makeText(context, "URL Saved", Toast.LENGTH_SHORT).show()
                                    // Trigger immediate test
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val url = URL(backendUrl.trimEnd('/').removeSuffix("/api/data"))
                                            val conn = url.openConnection() as HttpURLConnection
                                            conn.connectTimeout = 3000
                                            val code = conn.responseCode
                                            connectionStatus = (code == 200)
                                            conn.disconnect()
                                        } catch(e: Exception) { connectionStatus = false }
                                    }
                                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                                    Text("Save & Test")
                                }
                                Button(onClick = { 
                                    scope.launch { 
                                        Toast.makeText(context, "Bulk Sync Started...", Toast.LENGTH_SHORT).show()
                                        ApiSender.retryUnsent(context) 
                                        Toast.makeText(context, "Sync Batch Finished", Toast.LENGTH_SHORT).show()
                                    }
                                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                                    Text("Sync Bulk")
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun StatusIcon(label: String, active: Boolean, isPending: Boolean = false) {
    val color = when {
        isPending -> Color.Gray
        active -> AccentGreen
        else -> AccentRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TransactionItem(record: NotificationEntity) {
    val isSms = record.source == "sms"
    val accentColor = if (isSms) Color(0xFFFFD740) else Color(0xFF64FFDA)
    val senderInitial = (record.sender ?: record.title ?: "?").take(1).uppercase()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(senderInitial, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(record.sender ?: record.title ?: "System", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(record.body, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val time = try { record.timestampHuman.split(" ")[1].take(5) } catch(e: Exception) { "--:--" }
                Text(time, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("SMS", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SystemActionChip(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceBg.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Text(label, color = AccentBlue, fontSize = 13.sp)
    }
}
