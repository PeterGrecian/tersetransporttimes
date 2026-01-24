package com.tersetransporttimes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.tersetransporttimes.R
import com.tersetransporttimes.data.BusArrival
import com.tersetransporttimes.data.BusTimesResponse

@Composable
fun BusTimesScreen(viewModel: BusTimesViewModel) {
    val state by viewModel.state.collectAsState()
    val isRefreshing = state is BusTimesState.Loading

    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing),
        onRefresh = { viewModel.loadBusTimes() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1a1a1a))
        ) {
            when (val currentState = state) {
                is BusTimesState.Loading -> {
                    LoadingContent()
                }
                is BusTimesState.Success -> {
                    BusTimesList(currentState.data)
                }
                is BusTimesState.Error -> {
                    ErrorContent(
                        message = currentState.message,
                        onRetry = { viewModel.loadBusTimes() }
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF4a9eff))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading),
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = stringResource(R.string.error_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = Color(0xFFaaaaaa),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4a9eff)
                )
            ) {
                Text(
                    text = stringResource(R.string.retry),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun BusTimesList(data: BusTimesResponse) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "${data.route} - ${data.timestamp}",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Bus times list
        if (data.arrivals.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_buses),
                    color = Color(0xFFaaaaaa),
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(data.arrivals) { arrival ->
                    BusArrivalRow(arrival)
                    Divider(color = Color(0xFF333333), thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
fun BusArrivalRow(arrival: BusArrival) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minutes
        Text(
            text = "${arrival.minutes}m",
            color = Color(0xFF4a9eff),
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(50.dp)
        )

        // Direction
        Text(
            text = arrival.direction.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(40.dp)
        )

        // Stop name
        Text(
            text = arrival.stop,
            color = Color.White,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
