package com.anisoft.currencyconverter.data.remote

import com.anisoft.currencyconverter.BuildConfig
import com.anisoft.currencyconverter.utils.Constants
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val gson = GsonBuilder().create()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private fun createRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val frankfurterApi: FrankfurterApiService by lazy {
        createRetrofit(Constants.FRANKFURTER_BASE_URL).create(FrankfurterApiService::class.java)
    }

    val exchangeRateApi: ExchangeRateApiService by lazy {
        createRetrofit(Constants.EXCHANGE_RATE_BASE_URL).create(ExchangeRateApiService::class.java)
    }
}
