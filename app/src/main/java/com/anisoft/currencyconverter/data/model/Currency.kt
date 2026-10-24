package com.anisoft.currencyconverter.data.model

import java.util.Locale

data class Currency(
    val code: String,
    val name: String,
    val symbol: String
) {
    val displayName: String
        get() = "$code — $name"

    val searchKey: String
        get() = "$code $name $symbol".lowercase(Locale.ROOT)

    override fun toString(): String = displayName
}
