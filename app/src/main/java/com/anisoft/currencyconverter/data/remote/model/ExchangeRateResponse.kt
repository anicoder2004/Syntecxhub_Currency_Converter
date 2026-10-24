package com.anisoft.currencyconverter.data.remote.model

import com.google.gson.annotations.SerializedName

data class ExchangeRateResponse(
    @SerializedName("result")
    val result: String? = null,
    @SerializedName("base_code")
    val baseCode: String? = null,
    @SerializedName("rates")
    val rates: Map<String, Double>? = null,
    @SerializedName("time_last_update_unix")
    val timeLastUpdateUnix: Long? = null
)
