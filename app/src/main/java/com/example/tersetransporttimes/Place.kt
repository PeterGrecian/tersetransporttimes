package com.example.tersetransporttimes

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Where to pretend to be: Auto (GPS) for real use, the other six for testing and
// curiosity. They follow the STORIES.md stage table. Each still maps onto the
// four-way DebugLocation the fetch code understands, so the two Surbiton and the
// two Waterloo entries look alike until those stages get their own screens.
enum class Place(val label: String, val debug: DebugLocation) {
    AUTO("Auto", DebugLocation.AUTO),
    HOME("Home", DebugLocation.HOME),
    SUR_TO_WAT("SUR → WAT", DebugLocation.SURBITON),
    SUR_TO_HOME("SUR → home", DebugLocation.SURBITON),
    WAT_TO_WORK("WAT → work", DebugLocation.WATERLOO),
    WAT_TO_SUR("WAT → SUR", DebugLocation.WATERLOO),
    WORK("Work", DebugLocation.WORK),
}

@Composable
fun PlaceSelector(place: Place, onChange: (Place) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("📍 ${place.label} ▾", fontSize = 13.sp,
                color = if (place == Place.AUTO) Color(0xFF8E8E93) else Color(0xFFFF9500))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Place.values().forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { open = false; onChange(p) })
            }
        }
    }
}
