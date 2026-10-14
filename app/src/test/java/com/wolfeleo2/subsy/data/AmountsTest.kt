package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.util.formatAmountForInput
import com.wolfeleo2.subsy.data.util.parseAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AmountsTest {
    @Test
    fun parsesLocaleCommaDecimal() {
        assertEquals(9.99, parseAmount("9,99", Locale.GERMANY))
    }

    @Test
    fun parsesDotDecimalInCommaLocale() {
        assertEquals(9.99, parseAmount("9.99", Locale.GERMANY))
    }

    @Test
    fun parsesGroupingSeparators() {
        assertEquals(1000.0, parseAmount("1.000", Locale.GERMANY))
        assertEquals(1234.56, parseAmount("1.234,56", Locale.GERMANY))
        assertEquals(1234.56, parseAmount("1,234.56", Locale.US))
        assertEquals(1.0, parseAmount("1.000", Locale.US))
        assertNull(parseAmount("1,00", Locale.US))
    }

    @Test
    fun rejectsUnusableAmounts() {
        listOf("NaN", "Infinity", "-1", "1e7", "12abc", "").forEach { text ->
            assertNull("\"$text\" should not parse", parseAmount(text, Locale.US))
        }
    }

    @Test
    fun formatsWholeAmountWithoutDecimals() {
        assertEquals("20", formatAmountForInput(20.0, Locale.US))
    }

    @Test
    fun formatsLargeAmountWithoutExponent() {
        assertEquals("10000000", formatAmountForInput(1.0E7, Locale.US))
    }

    @Test
    fun formatsWithLocaleDecimalSeparator() {
        assertEquals("9,99", formatAmountForInput(9.99, Locale.GERMANY))
    }
}
