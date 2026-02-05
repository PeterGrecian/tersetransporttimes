package com.example.tersetransporttimes

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Navigation screens
sealed class Screen(val route: String, val title: String) {
    object Buses : Screen("buses", "Buses")
    object Trains : Screen("trains", "Trains")
}

// Train data models
data class TrainDeparture(
    val scheduledDeparture: String,
    val expectedDeparture: String,
    val eta: String,
    val journeyMins: Int,
    val stops: Int,
    val delayMinutes: Int,
    val cancelled: Boolean
)

data class TrainData(
    val originName: String,
    val destinationName: String,
    val departures: List<TrainDeparture>
)

// Parklands bus stop location
const val PARKLANDS_LAT = 51.39436
const val PARKLANDS_LON = -0.29321
const val HOME_RADIUS_METERS = 400f

// Surbiton Station location
const val SURBITON_LAT = 51.39374
const val SURBITON_LON = -0.30411
const val SURBITON_RADIUS_METERS = 400f

// Waterloo Station location
const val WATERLOO_LAT = 51.5031
const val WATERLOO_LON = -0.1132

// Debug location override for train times
enum class DebugLocation {
    AUTO,      // Use actual GPS location
    HOME,      // Force Parklands (morning commute: SUR→WAT)
    WATERLOO   // Force Waterloo (evening commute: WAT→SUR)
}

// Location mode determines which stop and directions to show
enum class LocationMode {
    NEAR_HOME,      // Near Parklands - show only inbound (to Kingston)
    NEAR_SURBITON,  // Near Surbiton Station - show only outbound (to Hook)
    ELSEWHERE       // Elsewhere - show both directions from Parklands
}

data class BusData(
    val stopName: String,
    val inboundSeconds: List<Int>?,
    val outboundSeconds: List<Int>?,
    val inboundDest: String?,
    val outboundDest: String?
)

fun secondsToQuarterMinutes(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return when {
        remainder < 15 -> "$minutes"
        remainder < 30 -> "$minutes¼"
        remainder < 45 -> "$minutes½"
        else -> "$minutes¾"
    }
}

fun distanceTo(lat: Double, lon: Double, targetLat: Double, targetLon: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(lat, lon, targetLat, targetLon, results)
    return results[0]
}

fun determineLocationMode(lat: Double, lon: Double): LocationMode {
    val distanceToHome = distanceTo(lat, lon, PARKLANDS_LAT, PARKLANDS_LON)
    val distanceToSurbiton = distanceTo(lat, lon, SURBITON_LAT, SURBITON_LON)

    return when {
        distanceToHome <= HOME_RADIUS_METERS -> LocationMode.NEAR_HOME
        distanceToSurbiton <= SURBITON_RADIUS_METERS -> LocationMode.NEAR_SURBITON
        else -> LocationMode.ELSEWHERE
    }
}

var currentRingtone: Ringtone? = null

fun playAlarmSound(context: Context) {
    try {
        stopAlarmSound() // Stop any existing alarm first
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        currentRingtone = RingtoneManager.getRingtone(context, alarmUri)
        currentRingtone?.play()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun stopAlarmSound() {
    try {
        currentRingtone?.stop()
        currentRingtone = null
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun isAlarmRinging(): Boolean {
    return try {
        currentRingtone?.isPlaying == true
    } catch (e: Exception) {
        false
    }
}

class MainActivity : ComponentActivity() {
    private var locationMode by mutableStateOf<LocationMode?>(null)

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                checkLocation()
            }
            else -> {
                // Permission denied - show both directions from Parklands
                locationMode = LocationMode.ELSEWHERE
            }
        }
    }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            checkLocation()
        } else {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MainScreen(locationMode)
        }
    }

    private fun checkLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            locationMode = LocationMode.ELSEWHERE
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                locationMode = determineLocationMode(location.latitude, location.longitude)
            } else {
                locationMode = LocationMode.ELSEWHERE
            }
        }.addOnFailureListener {
            locationMode = LocationMode.ELSEWHERE
        }
    }
}

// Alarm interval - sound alarm every 3 minutes before arrival
const val ALARM_INTERVAL_SECONDS = 180 // 3 minutes

@Composable
fun MainScreen(locationMode: LocationMode?) {
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Buses) }

    // Armed alarm state - hoisted to persist across tab navigation
    var armedBusKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAlarmThreshold by rememberSaveable { mutableIntStateOf(Int.MAX_VALUE) }
    var armedAtTime by rememberSaveable { mutableLongStateOf(0L) }

    Scaffold(
        containerColor = Color(0xFF1A1A1A),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF2A2A2A),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    icon = { Text("K2", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    label = { Text("Buses") },
                    selected = selectedScreen == Screen.Buses,
                    onClick = { selectedScreen = Screen.Buses },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4A9EFF),
                        selectedTextColor = Color(0xFF4A9EFF),
                        unselectedIconColor = Color(0xFF888888),
                        unselectedTextColor = Color(0xFF888888),
                        indicatorColor = Color(0xFF1A1A1A)
                    )
                )
                NavigationBarItem(
                    icon = { Text("SUR", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    label = { Text("Trains") },
                    selected = selectedScreen == Screen.Trains,
                    onClick = { selectedScreen = Screen.Trains },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4A9EFF),
                        selectedTextColor = Color(0xFF4A9EFF),
                        unselectedIconColor = Color(0xFF888888),
                        unselectedTextColor = Color(0xFF888888),
                        indicatorColor = Color(0xFF1A1A1A)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedScreen) {
                is Screen.Buses -> BusTimesScreen(
                    locationMode = locationMode,
                    armedBusKey = armedBusKey,
                    onArmedBusKeyChange = { armedBusKey = it },
                    lastAlarmThreshold = lastAlarmThreshold,
                    onLastAlarmThresholdChange = { lastAlarmThreshold = it },
                    armedAtTime = armedAtTime,
                    onArmedAtTimeChange = { armedAtTime = it }
                )
                is Screen.Trains -> TrainTimesScreen()
            }
        }
    }
}

@Composable
fun BusTimesScreen(
    locationMode: LocationMode?,
    armedBusKey: String?,
    onArmedBusKeyChange: (String?) -> Unit,
    lastAlarmThreshold: Int,
    onLastAlarmThresholdChange: (Int) -> Unit,
    armedAtTime: Long,
    onArmedAtTimeChange: (Long) -> Unit
) {
    val context = LocalContext.current
    var busData by remember { mutableStateOf<BusData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(30) }
    var lastFetchTime by remember { mutableStateOf(0L) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var lastRefreshDurationMs by remember { mutableLongStateOf(0L) }
    var showRefreshedMessage by remember { mutableStateOf(false) }

    // Determine which stop to fetch based on location
    val stopParam = when (locationMode) {
        LocationMode.NEAR_SURBITON -> "surbiton"
        else -> "parklands"
    }

    // Lifecycle observer to refresh when app resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = System.currentTimeMillis()
                val timeSinceLastFetch = now - lastFetchTime
                if (lastFetchTime > 0 && timeSinceLastFetch > 30_000) {
                    // Data is stale, trigger refresh
                    refreshTrigger++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Check if armed bus has crossed a 3-minute threshold
    fun checkAlarm(data: BusData?) {
        if (armedBusKey == null || data == null) return

        val seconds = when {
            armedBusKey!!.startsWith("inbound-") -> {
                val index = armedBusKey!!.removePrefix("inbound-").toIntOrNull() ?: return
                data.inboundSeconds?.getOrNull(index)
            }
            armedBusKey!!.startsWith("outbound-") -> {
                val index = armedBusKey!!.removePrefix("outbound-").toIntOrNull() ?: return
                data.outboundSeconds?.getOrNull(index)
            }
            else -> null
        }

        if (seconds != null) {
            // Calculate the next 3-minute threshold below current time
            // e.g., 500 seconds -> threshold is 360 (6 min)
            // e.g., 200 seconds -> threshold is 180 (3 min)
            val currentThreshold = (seconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS

            // If we've crossed below a new threshold, sound alarm
            // Add 15-second grace period after arming to prevent immediate alarm
            val timeSinceArmed = System.currentTimeMillis() - armedAtTime
            if (currentThreshold < lastAlarmThreshold && timeSinceArmed > 15_000) {
                playAlarmSound(context)
                onLastAlarmThresholdChange(currentThreshold)
            }
        } else {
            // Armed bus no longer exists in the data - auto-disarm
            onArmedBusKeyChange(null)
            onLastAlarmThresholdChange(Int.MAX_VALUE)
            stopAlarmSound()
            BusAlarmService.stopAlarm()
            context.stopService(Intent(context, BusAlarmService::class.java))
        }
    }

    // Handle refresh trigger from lifecycle
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            isLoading = true
            error = null
            val startTime = System.currentTimeMillis()
            try {
                delay(500) // Rate limit protection
                val data = fetchBusTimes(stopParam)
                busData = data
                lastFetchTime = System.currentTimeMillis()
                lastRefreshDurationMs = System.currentTimeMillis() - startTime
                checkAlarm(data)
                isLoading = false
                showRefreshedMessage = true
                countdown = 30
            } catch (e: Exception) {
                error = e.message ?: "No connection"
                isLoading = false
                countdown = 10
            }
        }
    }

    // Auto-refresh countdown
    LaunchedEffect(countdown, stopParam) {
        if (countdown > 0) {
            delay(1000)
            countdown--
            // Hide "refreshed in" message after 5 seconds
            if (countdown == 25) {
                showRefreshedMessage = false
            }
        } else {
            // Reload data
            isLoading = true
            error = null
            val startTime = System.currentTimeMillis()
            try {
                delay(500) // Rate limit protection
                val data = fetchBusTimes(stopParam)
                busData = data
                lastFetchTime = System.currentTimeMillis()
                lastRefreshDurationMs = System.currentTimeMillis() - startTime
                checkAlarm(data)
                isLoading = false
                showRefreshedMessage = true
                countdown = 30
            } catch (e: Exception) {
                error = e.message ?: "No connection"
                isLoading = false
                countdown = 10
            }
        }
    }

    // Initial load and reload when location mode changes
    LaunchedEffect(stopParam) {
        isLoading = true
        error = null
        val startTime = System.currentTimeMillis()
        try {
            delay(500) // Rate limit protection
            val data = fetchBusTimes(stopParam)
            busData = data
            lastFetchTime = System.currentTimeMillis()
            lastRefreshDurationMs = System.currentTimeMillis() - startTime
            checkAlarm(data)
            isLoading = false
            showRefreshedMessage = true
            countdown = 30
        } catch (e: Exception) {
            error = e.message ?: "No connection"
            isLoading = false
            countdown = 10
        }
    }

    // Determine header based on location
    val headerText = when (locationMode) {
        LocationMode.NEAR_SURBITON -> "K2 @ Surbiton Station"
        else -> "K2 @ Parklands"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = headerText,
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(top = 16.dp)
            )

            // Refresh countdown or status
            if (error == null || busData != null) {
                Text(
                    text = if (showRefreshedMessage) {
                        "refreshed in ${formatRefreshDuration(lastRefreshDurationMs)}"
                    } else {
                        "refresh in ${countdown}s"
                    },
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
                )
            }

            if (isLoading && busData == null) {
                CircularProgressIndicator(color = Color(0xFF4A9EFF))
            } else if (error != null && busData == null) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "Don't Panic!",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Trying again in ${countdown}s",
                    color = Color(0xFF888888),
                    fontSize = 16.sp
                )
            } else {
                busData?.let { data ->
                    when (locationMode) {
                        LocationMode.NEAR_HOME -> {
                            // Near Parklands - only show inbound (to Kingston)
                            data.inboundSeconds?.let { seconds ->
                                DirectionSection(
                                    seconds = seconds,
                                    destination = data.inboundDest ?: "Kingston",
                                    direction = "inbound",
                                    armedBusKey = armedBusKey,
                                    isLoading = isLoading,
                                    onTimeBoxClick = { key, currentSeconds ->
                                        if (isAlarmRinging() || BusAlarmService.isRinging()) {
                                            // Alarm is ringing - just stop the sound, stay armed
                                            stopAlarmSound()
                                            BusAlarmService.stopAlarm()
                                        } else if (armedBusKey == key) {
                                            // Armed but not ringing - disarm completely
                                            onArmedBusKeyChange(null)
                                            onLastAlarmThresholdChange(Int.MAX_VALUE)
                                            stopAlarmSound()
                                            BusAlarmService.stopAlarm()
                                            context.stopService(Intent(context, BusAlarmService::class.java))
                                        } else {
                                            // Not armed - arm this bus
                                            onArmedBusKeyChange(key)
                                            onArmedAtTimeChange(System.currentTimeMillis())
                                            // Calculate threshold, skipping immediate alarm if close to boundary
                                            val currentThreshold = (currentSeconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS
                                            val distanceToThreshold = currentSeconds - currentThreshold
                                            onLastAlarmThresholdChange(if (distanceToThreshold < 30) {
                                                // Close to boundary, skip this threshold
                                                maxOf(0, currentThreshold - ALARM_INTERVAL_SECONDS)
                                            } else {
                                                currentThreshold
                                            })
                                            stopAlarmSound()

                                            val parts = key.split("-")
                                            val direction = parts[0]
                                            val index = parts.getOrNull(1)?.toIntOrNull() ?: 0

                                            val serviceIntent = Intent(context, BusAlarmService::class.java).apply {
                                                putExtra(BusAlarmService.EXTRA_STOP, stopParam)
                                                putExtra(BusAlarmService.EXTRA_DIRECTION, direction)
                                                putExtra(BusAlarmService.EXTRA_INDEX, index)
                                                putExtra(BusAlarmService.EXTRA_INITIAL_SECONDS, currentSeconds)
                                            }
                                            context.startForegroundService(serviceIntent)
                                        }
                                    }
                                )
                            }
                        }
                        LocationMode.NEAR_SURBITON -> {
                            // Near Surbiton - only show outbound (to Hook)
                            data.outboundSeconds?.let { seconds ->
                                DirectionSection(
                                    seconds = seconds,
                                    destination = data.outboundDest ?: "Hook",
                                    direction = "outbound",
                                    armedBusKey = armedBusKey,
                                    isLoading = isLoading,
                                    onTimeBoxClick = { key, currentSeconds ->
                                        if (isAlarmRinging() || BusAlarmService.isRinging()) {
                                            // Alarm is ringing - just stop the sound, stay armed
                                            stopAlarmSound()
                                            BusAlarmService.stopAlarm()
                                        } else if (armedBusKey == key) {
                                            // Armed but not ringing - disarm completely
                                            onArmedBusKeyChange(null)
                                            onLastAlarmThresholdChange(Int.MAX_VALUE)
                                            stopAlarmSound()
                                            BusAlarmService.stopAlarm()
                                            context.stopService(Intent(context, BusAlarmService::class.java))
                                        } else {
                                            // Not armed - arm this bus
                                            onArmedBusKeyChange(key)
                                            onArmedAtTimeChange(System.currentTimeMillis())
                                            // Calculate threshold, skipping immediate alarm if close to boundary
                                            val currentThreshold = (currentSeconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS
                                            val distanceToThreshold = currentSeconds - currentThreshold
                                            onLastAlarmThresholdChange(if (distanceToThreshold < 30) {
                                                // Close to boundary, skip this threshold
                                                maxOf(0, currentThreshold - ALARM_INTERVAL_SECONDS)
                                            } else {
                                                currentThreshold
                                            })
                                            stopAlarmSound()

                                            val parts = key.split("-")
                                            val direction = parts[0]
                                            val index = parts.getOrNull(1)?.toIntOrNull() ?: 0

                                            val serviceIntent = Intent(context, BusAlarmService::class.java).apply {
                                                putExtra(BusAlarmService.EXTRA_STOP, stopParam)
                                                putExtra(BusAlarmService.EXTRA_DIRECTION, direction)
                                                putExtra(BusAlarmService.EXTRA_INDEX, index)
                                                putExtra(BusAlarmService.EXTRA_INITIAL_SECONDS, currentSeconds)
                                            }
                                            context.startForegroundService(serviceIntent)
                                        }
                                    }
                                )
                            }
                        }
                        else -> {
                            // Elsewhere - show both directions from Parklands
                            data.inboundSeconds?.let { seconds ->
                                DirectionSection(
                                    seconds = seconds,
                                    destination = data.inboundDest ?: "Kingston",
                                    direction = "inbound",
                                    armedBusKey = armedBusKey,
                                    isLoading = isLoading,
                                    onTimeBoxClick = { key, currentSeconds ->
                                        if (isAlarmRinging() || BusAlarmService.isRinging()) {
                                            // Alarm is ringing - just stop the sound, stay armed
                                            stopAlarmSound()
                                            BusAlarmService.stopAlarm()
                                        } else if (armedBusKey == key) {
                                            // Armed but not ringing - disarm completely
                                            onArmedBusKeyChange(null)
                                            onLastAlarmThresholdChange(Int.MAX_VALUE)
                                            stopAlarmSound()
                                            BusAlarmService.stopAlarm()
                                            context.stopService(Intent(context, BusAlarmService::class.java))
                                        } else {
                                            // Not armed - arm this bus
                                            onArmedBusKeyChange(key)
                                            onArmedAtTimeChange(System.currentTimeMillis())
                                            // Calculate threshold, skipping immediate alarm if close to boundary
                                            val currentThreshold = (currentSeconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS
                                            val distanceToThreshold = currentSeconds - currentThreshold
                                            onLastAlarmThresholdChange(if (distanceToThreshold < 30) {
                                                // Close to boundary, skip this threshold
                                                maxOf(0, currentThreshold - ALARM_INTERVAL_SECONDS)
                                            } else {
                                                currentThreshold
                                            })
                                            stopAlarmSound()

                                            val parts = key.split("-")
                                            val direction = parts[0]
                                            val index = parts.getOrNull(1)?.toIntOrNull() ?: 0

                                            val serviceIntent = Intent(context, BusAlarmService::class.java).apply {
                                                putExtra(BusAlarmService.EXTRA_STOP, stopParam)
                                                putExtra(BusAlarmService.EXTRA_DIRECTION, direction)
                                                putExtra(BusAlarmService.EXTRA_INDEX, index)
                                                putExtra(BusAlarmService.EXTRA_INITIAL_SECONDS, currentSeconds)
                                            }
                                            context.startForegroundService(serviceIntent)
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(48.dp))

                            data.outboundSeconds?.let { seconds ->
                                DirectionSection(
                                    seconds = seconds,
                                    destination = data.outboundDest ?: "Hook",
                                    direction = "outbound",
                                    armedBusKey = armedBusKey,
                                    isLoading = isLoading,
                                    onTimeBoxClick = { key, currentSeconds ->
                                        if (isAlarmRinging() || BusAlarmService.isRinging()) {
                                            // Alarm is ringing - just stop the sound, stay armed
                                            stopAlarmSound()
                                            BusAlarmService.stopAlarm()
                                        } else if (armedBusKey == key) {
                                            // Armed but not ringing - disarm completely
                                            onArmedBusKeyChange(null)
                                            onLastAlarmThresholdChange(Int.MAX_VALUE)
                                            stopAlarmSound()
                                            BusAlarmService.stopAlarm()
                                            context.stopService(Intent(context, BusAlarmService::class.java))
                                        } else {
                                            // Not armed - arm this bus
                                            onArmedBusKeyChange(key)
                                            onArmedAtTimeChange(System.currentTimeMillis())
                                            // Calculate threshold, skipping immediate alarm if close to boundary
                                            val currentThreshold = (currentSeconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS
                                            val distanceToThreshold = currentSeconds - currentThreshold
                                            onLastAlarmThresholdChange(if (distanceToThreshold < 30) {
                                                // Close to boundary, skip this threshold
                                                maxOf(0, currentThreshold - ALARM_INTERVAL_SECONDS)
                                            } else {
                                                currentThreshold
                                            })
                                            stopAlarmSound()

                                            val parts = key.split("-")
                                            val direction = parts[0]
                                            val index = parts.getOrNull(1)?.toIntOrNull() ?: 0

                                            val serviceIntent = Intent(context, BusAlarmService::class.java).apply {
                                                putExtra(BusAlarmService.EXTRA_STOP, stopParam)
                                                putExtra(BusAlarmService.EXTRA_DIRECTION, direction)
                                                putExtra(BusAlarmService.EXTRA_INDEX, index)
                                                putExtra(BusAlarmService.EXTRA_INITIAL_SECONDS, currentSeconds)
                                            }
                                            context.startForegroundService(serviceIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DirectionSection(
    seconds: List<Int>,
    destination: String,
    direction: String,
    armedBusKey: String?,
    isLoading: Boolean = false,
    onTimeBoxClick: (String, Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (seconds.isEmpty()) {
                TimeBox(displayText = null, isNext = false, isArmed = false, isLoading = isLoading, onClick = {})
            } else {
                seconds.forEachIndexed { index, secs ->
                    val key = "$direction-$index"
                    TimeBox(
                        displayText = secondsToQuarterMinutes(secs),
                        isNext = index == 0,
                        isArmed = armedBusKey == key,
                        isLoading = isLoading,
                        onClick = { onTimeBoxClick(key, secs) }
                    )
                    if (index < seconds.size - 1) {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "towards $destination",
            color = Color(0xFF666666),
            fontSize = 12.sp
        )
    }
}

@Composable
fun TimeBox(displayText: String?, isNext: Boolean, isArmed: Boolean, isLoading: Boolean = false, onClick: () -> Unit) {
    val borderColor = when {
        isLoading -> Color(0xFF444444) // Gray when loading
        isArmed -> Color(0xFFFF6B00) // Orange when armed
        isNext -> Color.White
        else -> Color(0xFF4A9EFF)
    }
    val textColor = when {
        isLoading -> Color(0xFF666666) // Gray when loading
        isArmed -> Color(0xFFFF6B00) // Orange when armed
        isNext -> Color.White
        else -> Color(0xFF4A9EFF)
    }
    val fontWeight = if (isNext || isArmed) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = Modifier
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText ?: "--",
            color = textColor,
            fontSize = 64.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center
        )
    }
}

private suspend fun fetchBusTimes(stop: String = "parklands"): BusData = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://vz66vhhtb9.execute-api.eu-west-1.amazonaws.com/t3?stop=$stop")
        .header("Accept", "application/json")
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Unexpected code $response")
        val json = JSONObject(response.body?.string() ?: "{}")

        val stopName = json.optString("stop", "Parklands")

        // Parse inbound if present
        var inboundSeconds: List<Int>? = null
        var inboundDest: String? = null
        if (json.has("inbound")) {
            val inbound = json.getJSONObject("inbound")
            val inboundList = mutableListOf<Int>()
            val inboundArr = inbound.getJSONArray("seconds")
            for (i in 0 until inboundArr.length()) {
                inboundList.add(inboundArr.getInt(i))
            }
            inboundSeconds = inboundList
            inboundDest = inbound.getString("destination")
        }

        // Parse outbound if present
        var outboundSeconds: List<Int>? = null
        var outboundDest: String? = null
        if (json.has("outbound")) {
            val outbound = json.getJSONObject("outbound")
            val outboundList = mutableListOf<Int>()
            val outboundArr = outbound.getJSONArray("seconds")
            for (i in 0 until outboundArr.length()) {
                outboundList.add(outboundArr.getInt(i))
            }
            outboundSeconds = outboundList
            outboundDest = outbound.getString("destination")
        }

        BusData(
            stopName = stopName,
            inboundSeconds = inboundSeconds,
            outboundSeconds = outboundSeconds,
            inboundDest = inboundDest,
            outboundDest = outboundDest
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun TrainTimesScreen() {
    val context = LocalContext.current
    var trainData by remember { mutableStateOf<TrainData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(30) }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    var lastRefreshDurationMs by remember { mutableLongStateOf(0L) }
    var showRefreshedMessage by remember { mutableStateOf(false) }
    var debugLocation by remember { mutableStateOf(DebugLocation.AUTO) }

    // Helper to get effective location (debug override or actual GPS)
    fun getEffectiveLocation(): Pair<Double?, Double?> {
        return when (debugLocation) {
            DebugLocation.HOME -> Pair(PARKLANDS_LAT, PARKLANDS_LON)
            DebugLocation.WATERLOO -> Pair(WATERLOO_LAT, WATERLOO_LON)
            DebugLocation.AUTO -> Pair(userLat, userLon)
        }
    }

    // Get location once on first load
    LaunchedEffect(Unit) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    userLat = location.latitude
                    userLon = location.longitude
                }
            }
        } catch (e: Exception) {
            // Location not available, will use default direction
        }
    }

    // Auto-refresh countdown
    LaunchedEffect(countdown, debugLocation) {
        if (countdown > 0) {
            delay(1000)
            countdown--
            // Hide "refreshed in" message after 5 seconds
            if (countdown == 25) {
                showRefreshedMessage = false
            }
        } else {
            isLoading = true
            error = null
            val startTime = System.currentTimeMillis()
            try {
                delay(500) // Rate limit protection
                val (lat, lon) = getEffectiveLocation()
                trainData = fetchTrainTimes(lat, lon)
                lastRefreshDurationMs = System.currentTimeMillis() - startTime
                isLoading = false
                showRefreshedMessage = true
                countdown = 30
            } catch (e: Exception) {
                error = e.message ?: "No connection"
                isLoading = false
                countdown = 10
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        try {
            delay(500)
            val (lat, lon) = getEffectiveLocation()
            trainData = fetchTrainTimes(lat, lon)
            isLoading = false
            countdown = 30
        } catch (e: Exception) {
            error = e.message ?: "No connection"
            isLoading = false
            countdown = 10
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = trainData?.let { "${it.originName} \u2192 ${it.destinationName}" }
                    ?: "Surbiton \u2192 Waterloo",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(top = 16.dp)
            )

            // Debug location buttons
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DebugLocation.values().forEach { loc ->
                    Button(
                        onClick = {
                            debugLocation = loc
                            countdown = 0  // Trigger immediate refresh
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (debugLocation == loc) Color(0xFF4A9EFF) else Color(0xFF2A2A2A)
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = when(loc) {
                                DebugLocation.AUTO -> "Auto"
                                DebugLocation.HOME -> "Home"
                                DebugLocation.WATERLOO -> "WAT"
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (error == null || trainData != null) {
                Text(
                    text = if (showRefreshedMessage) {
                        "refreshed in ${formatRefreshDuration(lastRefreshDurationMs)}"
                    } else {
                        "refresh in ${countdown}s"
                    },
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
            }

            if (isLoading && trainData == null) {
                CircularProgressIndicator(color = Color(0xFF4A9EFF))
            } else if (error != null && trainData == null) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "Don't Panic!",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Trying again in ${countdown}s",
                    color = Color(0xFF888888),
                    fontSize = 16.sp
                )
            } else {
                trainData?.departures?.forEach { departure ->
                    TrainDepartureRow(departure)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun TrainDepartureRow(departure: TrainDeparture) {
    val timeColor = when {
        departure.cancelled -> Color.Red
        departure.delayMinutes > 0 -> Color(0xFFFF9500) // Orange for delays
        else -> Color(0xFF4A9EFF)
    }

    val displayTime = formatTrainTime(departure.scheduledDeparture) +
        if (departure.delayMinutes > 0) "+${departure.delayMinutes}" else ""
    val etaTime = formatTrainTime(departure.eta)
    val tillDeparture = calculateMinutesTillDeparture(
        if (departure.expectedDeparture.isNotEmpty()) departure.expectedDeparture
        else departure.scheduledDeparture
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Departure time (gray if on time, orange if delayed)
        Text(
            text = displayTime,
            color = if (departure.delayMinutes > 0) Color(0xFFFF9500) else Color(0xFF888888),
            fontSize = 28.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        // Time till departure
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "in",
                color = Color(0xFF666666),
                fontSize = 10.sp
            )
            Text(
                text = tillDeparture,
                color = timeColor,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Journey info (stops + duration)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${departure.stops} stops",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
            Text(
                text = "${departure.journeyMins} min",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // ETA at Waterloo
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "arr",
                color = Color(0xFF666666),
                fontSize = 10.sp
            )
            Text(
                text = if (departure.cancelled) "CANC" else etaTime,
                color = timeColor,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

fun formatTrainTime(time: String): String {
    return if (time.length >= 4) {
        "${time.substring(0, 2)}:${time.substring(2, 4)}"
    } else {
        time
    }
}

fun formatRefreshDuration(durationMs: Long): String {
    return when {
        durationMs < 1000 -> "${durationMs}ms"
        durationMs < 10000 -> String.format("%.1fs", durationMs / 1000.0)
        else -> "${durationMs / 1000}s"
    }
}

fun calculateMinutesTillDeparture(departureTime: String): String {
    if (departureTime.isEmpty() || departureTime.length < 4) return "--"

    try {
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val currentSecond = now.get(java.util.Calendar.SECOND)

        val depHour = departureTime.substring(0, 2).toInt()
        val depMinute = departureTime.substring(2, 4).toInt()

        // Calculate total seconds till departure
        var secondsTillDep = (depHour * 3600 + depMinute * 60) -
                             (currentHour * 3600 + currentMinute * 60 + currentSecond)

        // Handle next day (trains after midnight)
        if (secondsTillDep < -43200) secondsTillDep += 86400

        if (secondsTillDep < 0) return "now"

        // Convert to quarter minutes (15-second resolution)
        val totalQuarters = (secondsTillDep + 7) / 15  // Round to nearest quarter
        val minutes = totalQuarters / 4
        val quarters = totalQuarters % 4

        return when (quarters) {
            0 -> "${minutes}"
            1 -> "${minutes}¼"
            2 -> "${minutes}½"
            3 -> "${minutes}¾"
            else -> "${minutes}"
        }
    } catch (e: Exception) {
        return "--"
    }
}

private suspend fun fetchTrainTimes(userLat: Double?, userLon: Double?): TrainData = withContext(Dispatchers.IO) {
    // Determine direction based on which station is closer
    val (fromStation, toStation) = if (userLat != null && userLon != null) {
        val distToSurbiton = distanceTo(userLat, userLon, SURBITON_LAT, SURBITON_LON)
        val distToWaterloo = distanceTo(userLat, userLon, WATERLOO_LAT, WATERLOO_LON)
        if (distToWaterloo < distToSurbiton) Pair("wat", "sur") else Pair("sur", "wat")
    } else {
        Pair("sur", "wat")  // Default: Surbiton to Waterloo
    }

    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://vz66vhhtb9.execute-api.eu-west-1.amazonaws.com/trains?from=$fromStation&to=$toStation")
        .header("Accept", "application/json")
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Unexpected code $response")
        val json = JSONObject(response.body?.string() ?: "{}")

        val departures = mutableListOf<TrainDeparture>()
        val departuresArray = json.optJSONArray("departures")

        if (departuresArray != null) {
            for (i in 0 until departuresArray.length()) {
                val dep = departuresArray.getJSONObject(i)
                departures.add(TrainDeparture(
                    scheduledDeparture = dep.optString("scheduledDeparture", ""),
                    expectedDeparture = dep.optString("expectedDeparture", ""),
                    eta = dep.optString("eta", ""),
                    journeyMins = dep.optInt("journeyMins", 0),
                    stops = dep.optInt("stops", 0),
                    delayMinutes = dep.optInt("delayMinutes", 0),
                    cancelled = dep.optBoolean("cancelled", false)
                ))
            }
        }

        TrainData(
            originName = json.optString("originName", "Surbiton"),
            destinationName = json.optString("destinationName", "London Waterloo"),
            departures = departures
        )
    }
}
