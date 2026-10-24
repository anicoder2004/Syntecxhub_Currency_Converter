package com.anisoft.currencyconverter.utils

import android.content.Context
import com.anisoft.currencyconverter.R
import java.text.NumberFormat
import java.util.Locale

object Formatters {

    fun formatAmount(value: Double): String {
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 4
        return format.format(value)
    }

    fun formatRate(value: Double): String {
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        if (value < 0.01) {
            format.minimumFractionDigits = 4
            format.maximumFractionDigits = 6
        } else {
            format.minimumFractionDigits = 2
            format.maximumFractionDigits = 4
        }
        return format.format(value)
    }

    fun parseAmount(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val normalized = when {
            trimmed.contains(',') && trimmed.contains('.') -> trimmed.replace(",", "")
            trimmed.contains(',') -> trimmed.replace(',', '.')
            else -> trimmed
        }

        if (normalized == "." || normalized == "-" || normalized == "-.") return null

        val value = normalized.toDoubleOrNull() ?: return null
        return value.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun cacheAge(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val elapsed = (now - timestamp).coerceAtLeast(0L)
        val minutes = elapsed / 60_000L
        val hours = elapsed / 3_600_000L
        val days = elapsed / 86_400_000L

        return when {
            minutes < 1L -> context.getString(R.string.cache_age_just_now)
            minutes < 60L -> context.getString(R.string.cache_age_minutes, minutes.toInt())
            hours < 24L -> context.getString(R.string.cache_age_hours, hours.toInt())
            else -> context.getString(R.string.cache_age_days, days.toInt())
        }
    }
}
