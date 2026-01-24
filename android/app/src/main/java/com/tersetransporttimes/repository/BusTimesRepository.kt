package com.tersetransporttimes.repository

import com.tersetransporttimes.api.ApiConfig
import com.tersetransporttimes.data.BusTimesResponse

class BusTimesRepository {
    private val api = ApiConfig.api
    private var cachedResponse: BusTimesResponse? = null

    suspend fun getBusTimes(): Result<BusTimesResponse> {
        return try {
            val response = api.getBusTimes()
            cachedResponse = response
            Result.success(response)
        } catch (e: Exception) {
            // Return cached data if available, otherwise return error
            cachedResponse?.let {
                Result.success(it)
            } ?: Result.failure(e)
        }
    }

    fun getCachedTimes(): BusTimesResponse? = cachedResponse
}
