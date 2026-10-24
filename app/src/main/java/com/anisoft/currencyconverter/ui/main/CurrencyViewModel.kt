package com.anisoft.currencyconverter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anisoft.currencyconverter.data.model.Currency
import com.anisoft.currencyconverter.data.repository.CurrencyRepository
import com.anisoft.currencyconverter.data.repository.FailureReason
import com.anisoft.currencyconverter.data.repository.RateResult
import com.anisoft.currencyconverter.ui.state.CurrencyUiState
import com.anisoft.currencyconverter.utils.Constants
import com.anisoft.currencyconverter.utils.CurrencyCatalog
import com.anisoft.currencyconverter.utils.Formatters
import com.anisoft.currencyconverter.utils.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CurrencyViewModel(
    private val repository: CurrencyRepository,
    private val preferences: PreferencesStore,
    initialBase: Currency,
    initialTarget: Currency
) : ViewModel() {

    private val _base = MutableStateFlow(initialBase)
    private val _target = MutableStateFlow(initialTarget)
    private val _amountText = MutableStateFlow(DEFAULT_AMOUNT)
    private val _rateResult = MutableStateFlow<RateResult?>(null)
    private val _isFetching = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<CurrencyUiState>(
        CurrencyUiState.Idle(initialBase, initialTarget, DEFAULT_AMOUNT)
    )
    val uiState: StateFlow<CurrencyUiState> = _uiState.asStateFlow()

    private val fetchRequests = Channel<FetchRequest>(Channel.CONFLATED)
    private var lastPairKey: String? = null

    init {
        observePairChanges()
        observeFetchRequests()
        observeState()
        purgeExpiredRates()
    }

    fun onAmountChanged(value: String) {
        _amountText.value = value
    }

    fun onBaseSelected(currency: Currency) {
        if (currency.code == _target.value.code) {
            _target.value = _base.value
        }
        _base.value = currency
    }

    fun onTargetSelected(currency: Currency) {
        if (currency.code == _base.value.code) {
            _base.value = _target.value
        }
        _target.value = currency
    }

    fun onSwap() {
        val currentBase = _base.value
        _base.value = _target.value
        _target.value = currentBase
    }

    fun onRetry() {
        fetchRequests.trySend(
            FetchRequest(
                base = _base.value,
                target = _target.value,
                forceRefresh = true
            )
        )
    }

    private fun observePairChanges() {
        viewModelScope.launch {
            combine(_base, _target) { base, target -> base to target }
                .distinctUntilChanged()
                .collect { (base, target) ->
                    val pairKey = Constants.pairId(base.code, target.code)
                    if (pairKey != lastPairKey) {
                        lastPairKey = pairKey
                        _rateResult.value = null
                    }
                    preferences.savePair(base.code, target.code)
                    fetchRequests.send(
                        FetchRequest(
                            base = base,
                            target = target,
                            forceRefresh = false
                        )
                    )
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFetchRequests() {
        viewModelScope.launch {
            fetchRequests.receiveAsFlow()
                .flatMapLatest { request ->
                    flow {
                        _isFetching.value = true
                        emit(
                            repository.getRate(
                                baseCode = request.base.code,
                                targetCode = request.target.code,
                                forceRefresh = request.forceRefresh
                            )
                        )
                    }
                }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _isFetching.value = false
                    emit(
                        RateResult.Failure(
                            reason = FailureReason.UNKNOWN,
                            message = throwable.message
                        )
                    )
                }
                .collect { result ->
                    _rateResult.value = result
                    _isFetching.value = false
                }
        }
    }

    private fun observeState() {
        viewModelScope.launch {
            combine(
                _base,
                _target,
                _amountText,
                _rateResult,
                _isFetching
            ) { base, target, amountText, result, isFetching ->
                buildState(base, target, amountText, result, isFetching)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun buildState(
        base: Currency,
        target: Currency,
        amountText: String,
        result: RateResult?,
        isFetching: Boolean
    ): CurrencyUiState {
        val parsedAmount = Formatters.parseAmount(amountText)

        return when (result) {
            null -> {
                if (isFetching) {
                    CurrencyUiState.Loading(base, target, amountText)
                } else {
                    CurrencyUiState.Idle(base, target, amountText)
                }
            }

            is RateResult.Failure -> CurrencyUiState.Error(
                base = base,
                target = target,
                amountText = amountText,
                reason = result.reason,
                message = result.message
            )

            is RateResult.Success -> CurrencyUiState.Success(
                base = base,
                target = target,
                amountText = amountText,
                rate = result.rate,
                convertedAmount = parsedAmount?.let { it * result.rate },
                isCached = result.isCached,
                isStale = result.isStale,
                sourceApi = result.sourceApi,
                rateTimestamp = result.timestamp,
                isRefreshing = isFetching
            )
        }
    }

    private fun purgeExpiredRates() {
        viewModelScope.launch {
            repository.purgeExpiredRates()
        }
    }

    private data class FetchRequest(
        val base: Currency,
        val target: Currency,
        val forceRefresh: Boolean
    )

    companion object {
        private const val DEFAULT_AMOUNT = "1"

        fun factory(
            repository: CurrencyRepository,
            preferences: PreferencesStore,
            catalog: CurrencyCatalog
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val fallbackBase = Currency("USD", "United States Dollar", "$")
                val fallbackTarget = Currency("BDT", "Bangladeshi Taka", "৳")

                val base = catalog.find(preferences.baseCurrencyCode)
                    ?: catalog.find(Constants.DEFAULT_BASE_CODE)
                    ?: fallbackBase

                var target = catalog.find(preferences.targetCurrencyCode)
                    ?: catalog.find(Constants.DEFAULT_TARGET_CODE)
                    ?: fallbackTarget

                if (target.code == base.code) {
                    target = fallbackTarget.takeIf { it.code != base.code } ?: fallbackBase
                }

                CurrencyViewModel(
                    repository = repository,
                    preferences = preferences,
                    initialBase = base,
                    initialTarget = target
                )
            }
        }
    }
}
