package com.tersetransporttimes.api

import com.tersetransporttimes.data.BusTimesResponse
import retrofit2.http.GET
import retrofit2.http.Headers

interface BusTimesApi {
    @Headers("Accept: application/json")
    @GET("/")
    suspend fun getBusTimes(): BusTimesResponse
}
