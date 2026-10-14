package com.wolfeleo2.subsy.data.util

import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.BillingCycleType
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class CategorySpend(
    val category: Category,
    val monthlyTotal: Double,
    val share: Float,
    val subscriptionCount: Int,
)

data class MonthSpend(
    val month: YearMonth,
    val total: Double,
)

/** [months] is oldest first and always [MonthlySpend.months].size long; payments that could not be converted are counted, not summed. */
data class MonthlySpend(
    val months: List<MonthSpend>,
    val unconvertedCount: Int,
)

data class CategoryTotal(
    val key: String,
    val total: Double,
)

/** One calendar month of money in and out, in one currency. Subscription payments count as spending. */
data class MonthSummary(
    val month: YearMonth,
    val income: Double,
    val expenses: Double,
    /** Spending per category key, largest first. Subscription payments are under [SpendCategory.SUBSCRIPTIONS]. */
    val expensesByCategory: List<CategoryTotal>,
    /** Items left out because no exchange rate was available. */
    val unconvertedCount: Int,
) {
    val net: Double get() = income - expenses
}

object ExpenseCalculations {
    fun monthSummary(
        transactions: List<Transaction>,
        payments: List<PaymentRecord>,
        currency: String,
        rates: ExchangeRates?,
        month: YearMonth,
        spread: Map<String, Int> = emptyMap(),
    ): MonthSummary {
        var income = 0.0
        var unconverted = 0
        val byCategory = mutableMapOf<String, Double>()

        fun convert(
            amount: Double,
            from: String,
        ): Double? = if (from == currency) amount else rates?.convert(amount, from, currency)

        for (t in transactions) {
            if (YearMonth.from(t.date.toLocalDate()) != month) continue
            val amount = convert(t.amount, t.currency)
            when {
                amount == null -> unconverted++
                t.type == TransactionType.INCOME -> income += amount
                else -> byCategory.merge(t.categoryKey, amount, Double::plus)
            }
        }
        for (p in payments) {
            val share = p.shareIn(month, spread[p.subscriptionId] ?: 1) ?: continue
            val amount = convert(share, p.currency)
            if (amount == null) unconverted++ else byCategory.merge(SpendCategory.SUBSCRIPTIONS.name, amount, Double::plus)
        }
        return MonthSummary(
            month = month,
            income = income,
            expenses = byCategory.values.sum(),
            expensesByCategory = byCategory.map { CategoryTotal(it.key, it.value) }.sortedByDescending { it.total },
            unconvertedCount = unconverted,
        )
    }

    /** Payments per calendar month for the last [months] months ending with the current one, in [currency]. */
    fun monthlyTotals(
        records: List<PaymentRecord>,
        currency: String,
        rates: ExchangeRates?,
        months: Int = 12,
        today: LocalDate = today(),
        spread: Map<String, Int> = emptyMap(),
    ): MonthlySpend {
        val current = YearMonth.from(today)
        val totals = linkedMapOf<YearMonth, Double>()
        for (back in months - 1 downTo 0) totals[current.minusMonths(back.toLong())] = 0.0
        return addPayments(totals, records, currency, rates, spread)
    }

    /** Payments per calendar month for months 1 through 12 (Jan to Dec) of [year], in [currency]. */
    fun calendarYearTotals(
        records: List<PaymentRecord>,
        currency: String,
        rates: ExchangeRates?,
        year: Int = today().year,
        spread: Map<String, Int> = emptyMap(),
    ): MonthlySpend {
        val totals = linkedMapOf<YearMonth, Double>()
        for (month in 1..12) totals[YearMonth.of(year, month)] = 0.0
        return addPayments(totals, records, currency, rates, spread)
    }

    /** Adds each payment (or its share, when spread) to the months already in [totals]. */
    private fun addPayments(
        totals: LinkedHashMap<YearMonth, Double>,
        records: List<PaymentRecord>,
        currency: String,
        rates: ExchangeRates?,
        spread: Map<String, Int>,
    ): MonthlySpend {
        var unconverted = 0
        for (record in records) {
            val amount = if (record.currency == currency) record.amount else rates?.convert(record.amount, record.currency, currency)
            if (amount == null) {
                unconverted++
                continue
            }
            val months = spread[record.subscriptionId] ?: 1
            val paid = YearMonth.from(record.paymentDate.toLocalDate())
            for (offset in 0 until months) {
                val month = paid.plusMonths(offset.toLong())
                if (month in totals) totals[month] = totals.getValue(month) + amount / months
            }
        }
        return MonthlySpend(totals.map { MonthSpend(it.key, it.value) }, unconverted)
    }

    fun calculateTotalMonthlyBurnRate(subscriptions: List<Subscription>): Double = subscriptions.sumOf { it.monthlyBurnRate }

    fun calculateTotalYearlyProjectedCost(subscriptions: List<Subscription>): Double = calculateTotalMonthlyBurnRate(subscriptions) * 12.0

    fun calculateActiveTrialsCount(subscriptions: List<Subscription>): Int = subscriptions.count { it.status == SubscriptionStatus.TRIAL }

    /** What the burn rate grows by if every running trial converts. */
    fun calculateTrialConversionCost(subscriptions: List<Subscription>): Double =
        subscriptions.filter { it.status == SubscriptionStatus.TRIAL }.sumOf { it.monthlyCost }

    /** Categories with active spend, largest first. */
    fun calculateCategoryBreakdown(subscriptions: List<Subscription>): List<CategorySpend> {
        val active = subscriptions.filter { it.status == SubscriptionStatus.ACTIVE }
        val total = active.sumOf { it.monthlyCost }
        return active
            .groupBy { it.category }
            .map { (category, subs) ->
                val sum = subs.sumOf { it.monthlyCost }
                CategorySpend(category, sum, if (total > 0) (sum / total).toFloat() else 0f, subs.size)
            }.sortedByDescending { it.monthlyTotal }
    }

    /** Trials ending today or within [withinDays] calendar days, soonest first. */
    fun calculateExpiringSoonTrials(
        subscriptions: List<Subscription>,
        withinDays: Long = 3,
        today: LocalDate = today(),
    ): List<Subscription> {
        val window = today..today.plusDays(withinDays)
        return subscriptions
            .filter { it.status == SubscriptionStatus.TRIAL && it.trialEndDate?.toLocalDate() in window }
            .sortedBy { it.trialEndDate }
    }
}

/** This payment's share of [month] when spread evenly over [months] months from the one it was paid in; null if none. */
fun PaymentRecord.shareIn(
    month: YearMonth,
    months: Int,
): Double? {
    val paid = YearMonth.from(paymentDate.toLocalDate())
    val offset = paid.until(month, ChronoUnit.MONTHS)
    return if (offset in 0 until months) amount / months else null
}

/** Months one charge covers, for spreading it: 3 for quarterly, 12 for yearly, 1 for monthly or shorter. */
fun BillingCycle.monthsCovered(): Int =
    when (type) {
        BillingCycleType.WEEKLY, BillingCycleType.MONTHLY -> 1
        BillingCycleType.QUARTERLY -> 3
        BillingCycleType.BI_ANNUALLY -> 6
        BillingCycleType.YEARLY -> 12
        BillingCycleType.CUSTOM -> ((customDays ?: 30) / DAYS_PER_MONTH).roundToInt().coerceAtLeast(1)
    }

private const val DAYS_PER_MONTH = 30.4375

/** Subscription id to months covered, for the subscriptions whose charges span more than one month. */
fun spreadMap(subscriptions: List<Subscription>): Map<String, Int> =
    subscriptions.associate { it.id to it.billingCycle.monthsCovered() }.filterValues { it > 1 }

/** What can still be spent each day: [left] split over the days remaining in [month], counting [today]. */
data class Allowance(
    val perDay: Double,
    val days: Int,
)

/** Null outside the current month, or once nothing is left. */
fun dailyAllowance(
    left: Double,
    month: YearMonth,
    today: LocalDate = today(),
): Allowance? {
    if (YearMonth.from(today) != month || left <= 0.0) return null
    val days = month.lengthOfMonth() - today.dayOfMonth + 1
    return Allowance(left / days, days)
}

/** The first [max] of [items] and how many more there are, for a row of logos ending in "+3". */
fun <T> topWithOverflow(
    items: List<T>,
    max: Int,
): Pair<List<T>, Int> = items.take(max) to (items.size - max).coerceAtLeast(0)
