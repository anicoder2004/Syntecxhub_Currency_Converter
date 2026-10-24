package com.anisoft.currencyconverter.utils

import android.content.Context
import com.anisoft.currencyconverter.data.model.Currency
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class CurrencyCatalog private constructor(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var cache: List<Currency>? = null

    fun currencies(): List<Currency> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: loadCurrencies().also { cache = it }
        }
    }

    fun find(code: String): Currency? = currencies().firstOrNull { it.code == code }

    private fun loadCurrencies(): List<Currency> {
        val json = appContext.assets.open(ASSET_NAME)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val array = JsonParser.parseString(json).asJsonArray

        return array.mapNotNull { element ->
            val item = element.asJsonObject
            val code = item.stringOrEmpty("code").trim()
            if (code.length != 3) return@mapNotNull null

            Currency(
                code = code,
                name = item.stringOrEmpty("name"),
                symbol = item.stringOrEmpty("symbol")
            )
        }.sortedBy { it.code }
    }

    private fun JsonObject.stringOrEmpty(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else value.asString
    }

    companion object {
        private const val ASSET_NAME = "currencies.json"

        @Volatile
        private var instance: CurrencyCatalog? = null

        fun getInstance(context: Context): CurrencyCatalog =
            instance ?: synchronized(this) {
                instance ?: CurrencyCatalog(context).also { instance = it }
            }
    }
}
