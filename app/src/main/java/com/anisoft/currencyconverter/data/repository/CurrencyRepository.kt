package com.anisoft.currencyconverter.data.repository

import com.anisoft.currencyconverter.data.local.CurrencyDao
import com.anisoft.currencyconverter.data.local.CurrencyEntity
import com.anisoft.currencyconverter.data.remote.ExchangeRateApiService
import com.anisoft.currencyconverter.data.remote.FrankfurterApiService
import com.anisoft.currencyconverter.utils.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CurrencyRepository(
    private val dao: CurrencyDao,
    private val frankfurterApi: FrankfurterApiService,
    private val exchangeRateApi: ExchangeRateApiService
) {

    suspend fun getRate(
        baseCode: String,
        targetCode: String,
        forceRefresh: Boolean = false
    ): RateResult = withContext(Dispatchers.IO) {
        if (baseCode == targetCode) {
            return@withContext RateResult.Success(
                rate = 1.0,
                isCached = false,
                isStale = false,
                sourceApi = Constants.SOURCE_FRANKFURTER,
                timestamp = System.currentTimeMillis()
            )
        }

        val pairId = Constants.pairId(baseCode, targetCode)
        val cachedRate = dao.getRate(pairId)
        val now = System.currentTimeMillis()

        if (!forceRefresh && cachedRate != null && isFresh(cachedRate, now)) {
            return@withContext cachedRate.toSuccess(isCached = true, isStale = false)
        }

        val primaryRate = fetchFromFrankfurter(baseCode, targetCode)
        if (primaryRate != null) {
            val saved = saveRate(
                baseCode = baseCode,
                targetCode = targetCode,
                rate = primaryRate,
                sourceApi = Constants.SOURCE_FRANKFURTER
            )
            return@withContext saved.toSuccess(isCached = false, isStale = false)
        }

        val fallback = fetchFromExchangeRate(baseCode, targetCode)
        if (fallback.rate != null) {
            val saved = saveRate(
                baseCode = baseCode,
                targetCode = targetCode,
                rate = fallback.rate,
                sourceApi = Constants.SOURCE_EXCHANGE_RATE
            )
            return@withContext saved.toSuccess(isCached = false, isStale = false)
        }

        if (cachedRate != null) {
            return@withContext cachedRate.toSuccess(isCached = true, isStale = true)
        }

        val failureReason = if (fallback.responded) {
            FailureReason.UNSUPPORTED_PAIR
        } else {
            FailureReason.NETWORK
        }

        RateResult.Failure(reason = failureReason)
    }

    suspend fun purgeExpiredRates() = withContext(Dispatchers.IO) {
        dao.deleteExpiredRates(System.currentTimeMillis() - Constants.CACHE_TTL_MILLIS)
    }

    private fun isFresh(entity: CurrencyEntity, now: Long): Boolean =
        now - entity.timestamp < Constants.CACHE_TTL_MILLIS

    private suspend fun fetchFromFrankfurter(baseCode: String, targetCode: String): Double? {
        return try {
            frankfurterApi.getRate(baseCode, targetCode)
                .rate
                ?.takeIf { it.isFinite() && it > 0.0 }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            null
        }
    }

    private suspend fun fetchFromExchangeRate(
        baseCode: String,
        targetCode: String
    ): FallbackOutcome {
        val response = try {
            exchangeRateApi.getLatest(baseCode)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            return FallbackOutcome(rate = null, responded = false)
        }

        if (!response.result.equals("success", ignoreCase = true)) {
            return FallbackOutcome(rate = null, responded = false)
        }

        val rate = response.rates?.get(targetCode)?.takeIf { it.isFinite() && it > 0.0 }
        return FallbackOutcome(rate = rate, responded = true)
    }

    private suspend fun saveRate(
        baseCode: String,
        targetCode: String,
        rate: Double,
        sourceApi: String
    ): CurrencyEntity {
        val entity = CurrencyEntity(
            id = Constants.pairId(baseCode, targetCode),
            baseCurrency = baseCode,
            targetCurrency = targetCode,
            rate = rate,
            timestamp = System.currentTimeMillis(),
            sourceApi = sourceApi
        )
        dao.insertRate(entity)
        return entity
    }

    private fun CurrencyEntity.toSuccess(
        isCached: Boolean,
        isStale: Boolean
    ): RateResult.Success =
        RateResult.Success(
            rate = rate,
            isCached = isCached,
            isStale = isStale,
            sourceApi = sourceApi,
            timestamp = timestamp
        )

    private data class FallbackOutcome(
        val rate: Double?,
        val responded: Boolean
    )
}
