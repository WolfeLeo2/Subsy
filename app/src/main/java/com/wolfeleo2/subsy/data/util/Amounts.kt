package com.wolfeleo2.subsy.data.util

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Parses a user-typed amount. Accepts the locale's decimal separator and '.', plus the locale's grouping separator in well-formed groups of three ("1.234,56"), rejects
 * trailing junk, NaN, infinities and negatives. Returns null when the text is not a usable amount.
 */
fun parseAmount(
    text: String,
    locale: Locale = Locale.getDefault(),
): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val symbols = DecimalFormatSymbols.getInstance(locale)
    val decimal = symbols.decimalSeparator
    val group = Regex.escape(symbols.groupingSeparator.toString())
    val grouped = Regex("""\d{1,3}($group\d{3})+(${Regex.escape(decimal.toString())}\d+)?""")
    val ungrouped = if (grouped.matches(trimmed)) trimmed.replace(symbols.groupingSeparator.toString(), "") else trimmed
    val normalized = ungrouped.replace(decimal, '.')
    if (!normalized.matches(Regex("""\d+(\.\d+)?|\.\d+"""))) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
}

/** Formats an amount for editing: no grouping, no trailing zeros, locale decimal separator ("20", "9,99"). */
fun formatAmountForInput(
    amount: Double,
    locale: Locale = Locale.getDefault(),
): String {
    val plain = BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return plain.replace('.', separator)
}
