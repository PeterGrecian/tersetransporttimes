package com.tersetransporttimes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tersetransporttimes.ui.BusTimesScreen
import com.tersetransporttimes.ui.BusTimesViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: BusTimesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    BusTimesScreen(viewModel)
                }
            }
        }
    }
}
