package com.fortnitecloudsync.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val baseHeaders = mapOf(
        "User-Agent" to "Fortnite/++UE4+Release-4.25-CL-14930499 Windows/10.0.19041.1.256.64bit",
        "Accept" to "application/json"
    )

    private fun buildOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Changed to BODY for debugging
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    baseHeaders.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }.build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val okHttpClient = buildOkHttpClient()

    val epicGamesApi: EpicGamesApiService = Retrofit.Builder()
        .baseUrl("https://account-public-service-prod03.ol.epicgames.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(EpicGamesApiService::class.java)

    val fortniteApi: FortniteApiService = Retrofit.Builder()
        .baseUrl("https://fortnite-public-service-prod11.ol.epicgames.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(FortniteApiService::class.java)
}
