package com.anisoft.currencyconverter.data.repository

sealed interface RateResult {

    data class Success(
        val rate: Double,
        val isCached: Boolean,
        val isStale: Boolean,
        val sourceApi: String,
        val timestamp: Long
    ) : RateResult

    data class Failure(
        val reason: FailureReason,
        val message: String? = null
    ) : RateResult
}

enum class FailureReason {
    NETWORK,
    UNSUPPORTED_PAIR,
    UNKNOWN
}
