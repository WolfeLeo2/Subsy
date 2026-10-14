package com.wolfeleo2.subsy.data.model

import com.wolfeleo2.subsy.data.repository.ExchangeRates
import java.time.YearMonth

/** An amount with the currency it was entered in, converted only when read (like subscriptions and transactions). */
data class Money(
    val amount: Double,
    val currency: String,
)

/** This amount in [currency] at today's rate, or null when no rate is known. */
fun Money.convertTo(
    currency: String,
    rates: ExchangeRates?,
): Double? = if (this.currency == currency) amount else rates?.convert(amount, this.currency, currency)

/** The usual monthly budget plus budgets set for single months (a holiday month, December). */
data class BudgetPlan(
    val default: Money? = null,
    val overrides: Map<YearMonth, Money> = emptyMap(),
) {
    fun forMonth(month: YearMonth): Money? = overrides[month] ?: default
}
