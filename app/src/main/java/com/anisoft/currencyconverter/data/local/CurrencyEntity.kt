package com.anisoft.currencyconverter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_rates")
data class CurrencyEntity(
    @PrimaryKey
    val id: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApi: String
)
