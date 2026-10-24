package com.anisoft.currencyconverter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CurrencyDao {

    @Query("SELECT * FROM cached_rates WHERE id = :pairId LIMIT 1")
    suspend fun getRate(pairId: String): CurrencyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: CurrencyEntity)

    @Query("DELETE FROM cached_rates WHERE timestamp < :expiryTime")
    suspend fun deleteExpiredRates(expiryTime: Long)
}
