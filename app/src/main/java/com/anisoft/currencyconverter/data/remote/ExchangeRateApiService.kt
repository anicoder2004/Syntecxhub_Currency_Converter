package com.anisoft.currencyconverter.data.remote

import com.anisoft.currencyconverter.data.remote.model.ExchangeRateResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApiService {

    @GET("v6/latest/{base}")
    suspend fun getLatest(@Path("base") base: String): ExchangeRateResponse
}
