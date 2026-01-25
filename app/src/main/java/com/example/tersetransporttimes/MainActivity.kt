package com.example.tersetransporttimes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class Arrival(val minutes: String, val dir: String, val stop: String)

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
    var arrivals by remember { mutableStateOf<List<Arrival>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val result = fetchBusTimes()
            arrivals = parseArrivals(result)
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "K2 Bus Times",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF4A9EFF))
            } else if (error != null) {
                Text(text = "Error: $error", color = Color.Red)
            } else {
                LazyColumn {
                    items(arrivals) { arrival ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "${arrival.minutes}m",
                                color = Color(0xFF4A9EFF),
                                modifier = Modifier.width(40.dp),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = arrival.dir,
                                color = Color.LightGray,
                                modifier = Modifier.width(30.dp),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = arrival.stop,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun fetchBusTimes(): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    // REPLACE with your actual Lambda/API URL
    val request = Request.Builder()
        .url("https://example.com/api/bus") 
        .header("Accept", "text/plain")
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Unexpected code $response")
        response.body?.string() ?: ""
    }
}

private fun parseArrivals(text: String): List<Arrival> {
    // Basic parser for the format: "  5m  N  Stop Name"
    return text.lines()
        .filter { it.contains("m ") }
        .mapNotNull { line ->
            try {
                val parts = line.trim().split(Regex("\\s+"), limit = 3)
                if (parts.size >= 3) {
                    Arrival(parts[0].replace("m", ""), parts[1], parts[2])
                } else null
            } catch (e: Exception) {
                null
            }
        }
}
