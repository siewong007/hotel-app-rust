package com.hotelapp.staff.core.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormatter {
    private val decimalFormatSymbols = DecimalFormatSymbols(Locale.US)

    fun format(amount: BigDecimal?, currencyCode: String = "MYR"): String {
        val value = (amount ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        val isNegative = value < BigDecimal.ZERO
        val absNumberStr = DecimalFormat("#,##0.00", decimalFormatSymbols).format(value.abs())
        val code = currencyCode.trim().uppercase(Locale.US).ifEmpty { "MYR" }
        val symbol = when (code) {
            "MYR" -> "RM "
            "USD" -> "$"
            "SGD" -> "S$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "$code "
        }
        return if (isNegative) "-$symbol$absNumberStr" else "$symbol$absNumberStr"
    }
}

object MoneyInputParser {
    private val defaultMaxAmount = BigDecimal("9999999.99")

    fun parsePositiveAmount(
        raw: String,
        maxAmount: BigDecimal = defaultMaxAmount,
    ): BigDecimal? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parsed = trimmed.toBigDecimalOrNull() ?: return null
        if (parsed <= BigDecimal.ZERO) return null
        if (parsed > maxAmount) return null
        if (parsed.stripTrailingZeros().scale() > 2) return null
        return parsed
    }
}
