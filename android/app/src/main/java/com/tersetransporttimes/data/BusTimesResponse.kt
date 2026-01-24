package com.tersetransporttimes.data

data class BusTimesResponse(
    val timestamp: String,
    val route: String,
    val arrivals: List<BusArrival>
)
