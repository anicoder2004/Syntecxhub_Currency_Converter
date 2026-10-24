package com.anisoft.currencyconverter.utils

object Constants {

    const val FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/"
    const val EXCHANGE_RATE_BASE_URL = "https://open.er-api.com/"

    const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L
    const val PAIR_SEPARATOR = "_"

    const val SOURCE_FRANKFURTER = "FRANKFURTER"
    const val SOURCE_EXCHANGE_RATE = "EXCHANGERATE_API"

    const val DEFAULT_BASE_CODE = "USD"
    const val DEFAULT_TARGET_CODE = "BDT"

    fun pairId(baseCode: String, targetCode: String): String =
        "$baseCode$PAIR_SEPARATOR$targetCode"
}
