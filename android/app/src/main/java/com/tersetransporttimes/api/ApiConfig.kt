package com.tersetransporttimes.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiConfig {
    // Switch between mock and production backends
    private const val USE_MOCK = true  // Set to false when using real AWS backend

    // Mock backend URLs (for local testing)
    // - Android Emulator: Use 10.0.2.2 to access host machine
    // - Real device: Use your computer's IP address (e.g., "192.168.1.100")
    private const val MOCK_URL = "http://10.0.2.2:8000/"

    // Production AWS Lambda endpoint
    // Get this from GitHub Actions deployment summary or AWS Console
    private const val PROD_URL = "https://your-api-gateway-url.execute-api.eu-west-1.amazonaws.com/"

    private const val BASE_URL = if (USE_MOCK) MOCK_URL else PROD_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: BusTimesApi = retrofit.create(BusTimesApi::class.java)
}
