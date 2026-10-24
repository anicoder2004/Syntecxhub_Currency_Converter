package com.anisoft.currencyconverter.utils

import android.content.Context

class PreferencesStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    val baseCurrencyCode: String
        get() = preferences.getString(KEY_BASE_CURRENCY, Constants.DEFAULT_BASE_CODE)
            ?: Constants.DEFAULT_BASE_CODE

    val targetCurrencyCode: String
        get() = preferences.getString(KEY_TARGET_CURRENCY, Constants.DEFAULT_TARGET_CODE)
            ?: Constants.DEFAULT_TARGET_CODE

    fun savePair(baseCode: String, targetCode: String) {
        preferences.edit()
            .putString(KEY_BASE_CURRENCY, baseCode)
            .putString(KEY_TARGET_CURRENCY, targetCode)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "currency_converter_prefs"
        const val KEY_BASE_CURRENCY = "base_currency"
        const val KEY_TARGET_CURRENCY = "target_currency"
    }
}
