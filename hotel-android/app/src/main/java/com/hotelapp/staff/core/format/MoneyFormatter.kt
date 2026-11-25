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
        val numberStr = DecimalFormat("#,##0.00", decimalFormatSymbols).format(value)
        val code = currencyCode.trim().uppercase(Locale.US).ifEmpty { "MYR" }
        return when (code) {
            "MYR" -> "RM $numberStr"
            "USD" -> "$$numberStr"
            "SGD" -> "S$$numberStr"
            "EUR" -> "€$numberStr"
            "GBP" -> "£$numberStr"
            else -> "$code $numberStr"
        }
    }
}
