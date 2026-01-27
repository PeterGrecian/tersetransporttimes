package com.example.tersetransporttimes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.media.Ringtone
import android.media.RingtoneManager
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

// Parklands bus stop location
const val PARKLANDS_LAT = 51.39436
const val PARKLANDS_LON = -0.29321
const val HOME_RADIUS_METERS = 400f

// Surbiton Station location
const val SURBITON_LAT = 51.39374
const val SURBITON_LON = -0.30411
const val SURBITON_RADIUS_METERS = 400f

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

        setContent {
            BusTimesScreen(locationMode)
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
fun BusTimesScreen(locationMode: LocationMode?) {
    val context = LocalContext.current
    var busData by remember { mutableStateOf<BusData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(30) }
    var lastFetchTime by remember { mutableStateOf(0L) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Armed alarm state: "inbound-0", "inbound-1", "outbound-0", "outbound-1", or null
    var armedBusKey by remember { mutableStateOf<String?>(null) }
    // Track the last 3-minute threshold that triggered an alarm (e.g., 540, 360, 180, 0)
    var lastAlarmThreshold by remember { mutableIntStateOf(Int.MAX_VALUE) }

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
            if (currentThreshold < lastAlarmThreshold) {
                playAlarmSound(context)
                lastAlarmThreshold = currentThreshold
            }
        }
    }

    // Handle refresh trigger from lifecycle
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            isLoading = true
            error = null
            try {
                delay(500) // Rate limit protection
                val data = fetchBusTimes(stopParam)
                busData = data
                lastFetchTime = System.currentTimeMillis()
                checkAlarm(data)
                isLoading = false
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
            countdown = 30
        }
    }

    // Auto-refresh countdown
    LaunchedEffect(countdown, stopParam) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        } else {
            // Reload data
            isLoading = true
            error = null
            try {
                delay(500) // Rate limit protection
                val data = fetchBusTimes(stopParam)
                busData = data
                lastFetchTime = System.currentTimeMillis()
                checkAlarm(data)
                isLoading = false
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
            countdown = 30
        }
    }

    // Initial load and reload when location mode changes
    LaunchedEffect(stopParam) {
        isLoading = true
        error = null
        try {
            delay(500) // Rate limit protection
            val data = fetchBusTimes(stopParam)
            busData = data
            lastFetchTime = System.currentTimeMillis()
            checkAlarm(data)
            isLoading = false
        } catch (e: Exception) {
            error = e.message
            isLoading = false
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

            // Refresh countdown
            Text(
                text = "refresh in ${countdown}s",
                color = Color(0xFF888888),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
            )

            if (isLoading && busData == null) {
                CircularProgressIndicator(color = Color(0xFF4A9EFF))
            } else if (error != null && busData == null) {
                Text(text = "Error: $error", color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        countdown = 0 // Trigger reload
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF))
                ) {
                    Text("Retry")
                }
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
                                    onTimeBoxClick = { key, currentSeconds ->
                                        if (armedBusKey == key) {
                                            armedBusKey = null
                                            lastAlarmThreshold = Int.MAX_VALUE
                                            stopAlarmSound()
                                        } else {
                                            armedBusKey = key
                                            // Set initial threshold to current level so it won't alarm immediately
                                            lastAlarmThreshold = (currentSeconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS + ALARM_INTERVAL_SECONDS
                                            stopAlarmSound()
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
                                    onTimeBoxClick = { key, currentSeconds ->
                                        if (armedBusKey == key) {
                                            armedBusKey = null
                                            lastAlarmThreshold = Int.MAX_VALUE
                                            stopAlarmSound()
                                        } else {
                                            armedBusKey = key
                                            // Set initial threshold to current level so it won't alarm immediately
                                            lastAlarmThreshold = (currentSeconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS + ALARM_INTERVAL_SECONDS
                                            stopAlarmSound()
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
                                    onTimeBoxClick = { key, currentSeconds ->
                                        if (armedBusKey == key) {
                                            armedBusKey = null
                                            lastAlarmThreshold = Int.MAX_VALUE
                                            stopAlarmSound()
                                        } else {
                                            armedBusKey = key
                                            // Set initial threshold to current level so it won't alarm immediately
                                            lastAlarmThreshold = (currentSeconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS + ALARM_INTERVAL_SECONDS
                                            stopAlarmSound()
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
                                    onTimeBoxClick = { key, currentSeconds ->
                                        if (armedBusKey == key) {
                                            armedBusKey = null
                                            lastAlarmThreshold = Int.MAX_VALUE
                                            stopAlarmSound()
                                        } else {
                                            armedBusKey = key
                                            // Set initial threshold to current level so it won't alarm immediately
                                            lastAlarmThreshold = (currentSeconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS + ALARM_INTERVAL_SECONDS
                                            stopAlarmSound()
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
    onTimeBoxClick: (String, Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (seconds.isEmpty()) {
                TimeBox(displayText = null, isNext = false, isArmed = false, onClick = {})
            } else {
                seconds.forEachIndexed { index, secs ->
                    val key = "$direction-$index"
                    TimeBox(
                        displayText = secondsToQuarterMinutes(secs),
                        isNext = index == 0,
                        isArmed = armedBusKey == key,
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
fun TimeBox(displayText: String?, isNext: Boolean, isArmed: Boolean, onClick: () -> Unit) {
    val borderColor = when {
        isArmed -> Color(0xFFFF6B00) // Orange when armed
        isNext -> Color.White
        else -> Color(0xFF4A9EFF)
    }
    val textColor = when {
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
        .url("https://w3.petergrecian.co.uk/t3?stop=$stop")
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
