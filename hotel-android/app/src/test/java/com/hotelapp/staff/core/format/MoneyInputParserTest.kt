package com.hotelapp.staff.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MoneyInputParserTest {

    @Test
    fun `parses valid positive amounts up to 2 decimal places`() {
        assertEquals(BigDecimal("120"), MoneyInputParser.parsePositiveAmount("120"))
        assertEquals(BigDecimal("120.5"), MoneyInputParser.parsePositiveAmount("120.5"))
        assertEquals(BigDecimal("120.50"), MoneyInputParser.parsePositiveAmount(" 120.50 "))
        assertEquals(BigDecimal("10.100"), MoneyInputParser.parsePositiveAmount("10.100"))
        assertEquals(BigDecimal("0.01"), MoneyInputParser.parsePositiveAmount("0.01"))
    }

    @Test
    fun `rejects blank, non-positive, invalid, excessive, and 3-decimal amounts`() {
        assertNull(MoneyInputParser.parsePositiveAmount(""))
        assertNull(MoneyInputParser.parsePositiveAmount("   "))
        assertNull(MoneyInputParser.parsePositiveAmount("0"))
        assertNull(MoneyInputParser.parsePositiveAmount("0.00"))
        assertNull(MoneyInputParser.parsePositiveAmount("-25.00"))
        assertNull(MoneyInputParser.parsePositiveAmount("NaN"))
        assertNull(MoneyInputParser.parsePositiveAmount("Infinity"))
        assertNull(MoneyInputParser.parsePositiveAmount("RM 100.00"))
        assertNull(MoneyInputParser.parsePositiveAmount("1,200.00"))
        assertNull(MoneyInputParser.parsePositiveAmount("10.123"))

        val cap = BigDecimal("150.00")
        assertEquals(BigDecimal("150.00"), MoneyInputParser.parsePositiveAmount("150.00", maxAmount = cap))
        assertNull(MoneyInputParser.parsePositiveAmount("150.01", maxAmount = cap))
    }
}
