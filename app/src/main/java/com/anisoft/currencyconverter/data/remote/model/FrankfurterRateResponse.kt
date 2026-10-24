package com.anisoft.currencyconverter.data.remote.model

import com.google.gson.annotations.SerializedName

data class FrankfurterRateResponse(
    @SerializedName("date")
    val date: String? = null,
    @SerializedName("base")
    val base: String? = null,
    @SerializedName("quote")
    val quote: String? = null,
    @SerializedName("rate")
    val rate: Double? = null
)
