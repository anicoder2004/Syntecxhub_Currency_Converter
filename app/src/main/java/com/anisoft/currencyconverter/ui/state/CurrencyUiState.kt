package com.anisoft.currencyconverter.ui.state

import com.anisoft.currencyconverter.data.model.Currency
import com.anisoft.currencyconverter.data.repository.FailureReason

sealed interface CurrencyUiState {

    data class Idle(
        val base: Currency,
        val target: Currency,
        val amountText: String
    ) : CurrencyUiState

    data class Loading(
        val base: Currency,
        val target: Currency,
        val amountText: String
    ) : CurrencyUiState

    data class Success(
        val base: Currency,
        val target: Currency,
        val amountText: String,
        val rate: Double,
        val convertedAmount: Double?,
        val isCached: Boolean,
        val isStale: Boolean,
        val sourceApi: String,
        val rateTimestamp: Long,
        val isRefreshing: Boolean
    ) : CurrencyUiState

    data class Error(
        val base: Currency,
        val target: Currency,
        val amountText: String,
        val reason: FailureReason,
        val message: String?
    ) : CurrencyUiState
}
