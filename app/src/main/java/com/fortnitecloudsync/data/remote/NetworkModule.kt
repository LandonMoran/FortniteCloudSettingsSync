package com.fortnitecloudsync.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

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
