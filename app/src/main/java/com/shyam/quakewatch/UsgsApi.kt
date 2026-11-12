package com.shyam.quakewatch

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/**
 * Retrofit interface for fetching USGS Earthquake GeoJSON feed.
 */
interface UsgsApiService {
    @GET("earthquakes/feed/v1.0/summary/all_day.geojson")
    suspend fun getEarthquakes(): QuakeResponse
}

/**
 * Singleton object providing configured Retrofit instance.
 */
object RetrofitInstance {
    private const val BASE_URL = "https://earthquake.usgs.gov/"

    val api: UsgsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UsgsApiService::class.java)
    }
}
