package com.anisoft.currencyconverter.data.remote

import com.anisoft.currencyconverter.data.remote.model.FrankfurterRateResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface FrankfurterApiService {

    @GET("v2/rate/{base}/{target}")
    suspend fun getRate(
        @Path("base") base: String,
        @Path("target") target: String
    ): FrankfurterRateResponse
}
