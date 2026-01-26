package com.example.tersetransporttimes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class BusData(
    val inboundSeconds: List<Int>,
    val outboundSeconds: List<Int>,
    val inboundDest: String,
    val outboundDest: String
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BusTimesScreen()
        }
    }
}

@Composable
fun BusTimesScreen() {
    var busData by remember { mutableStateOf<BusData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(30) }

    // Auto-refresh countdown
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        } else {
            // Reload data
            isLoading = true
            error = null
            try {
                busData = fetchBusTimes()
                isLoading = false
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
            countdown = 30
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        try {
            busData = fetchBusTimes()
            isLoading = false
        } catch (e: Exception) {
            error = e.message
            isLoading = false
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
            // Header
            Text(
                text = "K2 @ Parklands",
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
                    // Inbound section
                    DirectionSection(
                        seconds = data.inboundSeconds,
                        destination = data.inboundDest
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // Outbound section
                    DirectionSection(
                        seconds = data.outboundSeconds,
                        destination = data.outboundDest
                    )

                }
            }
        }
    }
}

@Composable
fun DirectionSection(seconds: List<Int>, destination: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (seconds.isEmpty()) {
                TimeBox(displayText = null, isNext = false)
            } else {
                seconds.forEachIndexed { index, secs ->
                    TimeBox(displayText = secondsToQuarterMinutes(secs), isNext = index == 0)
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
fun TimeBox(displayText: String?, isNext: Boolean) {
    val borderColor = if (isNext) Color.White else Color(0xFF4A9EFF)
    val textColor = if (isNext) Color.White else Color(0xFF4A9EFF)
    val fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = Modifier
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
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

private suspend fun fetchBusTimes(): BusData = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://w3.petergrecian.co.uk/t3")
        .header("Accept", "application/json")
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Unexpected code $response")
        val json = JSONObject(response.body?.string() ?: "{}")

        val inbound = json.getJSONObject("inbound")
        val outbound = json.getJSONObject("outbound")

        val inboundSeconds = mutableListOf<Int>()
        val inboundArr = inbound.getJSONArray("seconds")
        for (i in 0 until inboundArr.length()) {
            inboundSeconds.add(inboundArr.getInt(i))
        }

        val outboundSeconds = mutableListOf<Int>()
        val outboundArr = outbound.getJSONArray("seconds")
        for (i in 0 until outboundArr.length()) {
            outboundSeconds.add(outboundArr.getInt(i))
        }

        BusData(
            inboundSeconds = inboundSeconds,
            outboundSeconds = outboundSeconds,
            inboundDest = inbound.getString("destination"),
            outboundDest = outbound.getString("destination")
        )
    }
}
