package com.wolfeleo2.subsy.ui.util

import android.icu.number.Notation
import android.icu.number.NumberFormatter
import android.icu.number.Precision
import android.text.format.DateFormat
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.data.util.today
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

object FormattingUtils {
    fun formatCurrency(
        amount: Double,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
        wholeUnits: Boolean = false,
    ): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        val currency = runCatching { Currency.getInstance(currencyCode.uppercase(Locale.ROOT)) }.getOrNull()
        if (currency != null) {
            format.currency = currency
            format.maximumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
            format.minimumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
        }
        if (wholeUnits) {
            format.maximumFractionDigits = 0
            format.minimumFractionDigits = 0
        }
        return format.format(amount)
    }

    /** Short axis labels: "£40", "£1.2K". Falls back to a plain compact number for unknown currency codes. */
    fun formatCompactCurrency(
        amount: Double,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
    ): String {
        val base = NumberFormatter.withLocale(locale).notation(Notation.compactShort()).precision(Precision.maxSignificantDigits(2))
        val currency =
            runCatching {
                android.icu.util.Currency
                    .getInstance(currencyCode.uppercase(Locale.ROOT))
            }.getOrNull()
        return (if (currency == null) base else base.unit(currency)).format(amount).toString()
    }

    /** "Oct 14, 2026" in the user's locale. */
    fun formatDate(epochMillis: Long): String = epochMillis.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    /** "Oct 14" (or "14 Oct"), the locale's own month-day order. */
    fun formatMonthDay(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
    ): String = epochMillis.toLocalDate().format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale))

    /** Calendar days from today until [epochMillis]; negative when it is in the past. */
    fun daysUntil(epochMillis: Long): Long = ChronoUnit.DAYS.between(today(), epochMillis.toLocalDate())
}
