package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.model.CategoryRef
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.model.resolveCategory
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.util.CategoryTotal
import com.wolfeleo2.subsy.data.util.ExpenseCalculations
import com.wolfeleo2.subsy.data.util.toEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TransactionsDataTest {
    private val groceries = SpendCategory.GROCERIES.name

    private fun tx(
        amount: Double,
        date: LocalDate,
        type: TransactionType = TransactionType.EXPENSE,
        key: String = groceries,
        currency: String = "USD",
    ) = Transaction("$amount$date$type$key$currency", type, amount, currency, date.toEpochMillis(), key)

    @Test
    fun resolvesBuiltinCustomAndMissingKeys() {
        val custom = CustomCategory("c1", "Coffee", TransactionType.EXPENSE, "#000000")
        val customs = mapOf(custom.key to custom)
        assertEquals(CategoryRef.Builtin(SpendCategory.DINING), resolveCategory("DINING", TransactionType.EXPENSE, customs))
        assertEquals(CategoryRef.Custom(custom), resolveCategory("custom:c1", TransactionType.EXPENSE, customs))
        // A deleted custom category falls back to the kind's Other.
        assertEquals(CategoryRef.Builtin(SpendCategory.OTHER), resolveCategory("custom:gone", TransactionType.EXPENSE, customs))
        assertEquals(CategoryRef.Builtin(SpendCategory.OTHER_INCOME), resolveCategory("custom:gone", TransactionType.INCOME, customs))
    }

    @Test
    fun pickableListsHideSubscriptionsAndSplitByKind() {
        val expense = SpendCategory.pickable(TransactionType.EXPENSE)
        assert(SpendCategory.SUBSCRIPTIONS !in expense)
        assert(expense.all { it.kind == TransactionType.EXPENSE })
        assert(SpendCategory.pickable(TransactionType.INCOME).all { it.kind == TransactionType.INCOME })
    }

    @Test
    fun monthSummarySplitsInAndOutAndCountsSubscriptionPayments() {
        val month = YearMonth.of(2026, 10)
        val day = LocalDate.of(2026, 10, 5)
        val summary =
            ExpenseCalculations.monthSummary(
                transactions =
                    listOf(
                        tx(30.0, day),
                        tx(10.0, day, key = SpendCategory.DINING.name),
                        tx(1000.0, day, TransactionType.INCOME, SpendCategory.SALARY.name),
                        tx(99.0, LocalDate.of(2026, 9, 30)), // previous month
                    ),
                payments = listOf(PaymentRecord("p", "s", 15.0, "USD", day.toEpochMillis())),
                currency = "USD",
                rates = null,
                month = month,
            )
        assertEquals(1000.0, summary.income, 0.001)
        assertEquals(55.0, summary.expenses, 0.001)
        assertEquals(945.0, summary.net, 0.001)
        assertEquals(
            listOf(CategoryTotal(groceries, 30.0), CategoryTotal(SpendCategory.SUBSCRIPTIONS.name, 15.0), CategoryTotal("DINING", 10.0)),
            summary.expensesByCategory,
        )
    }

    @Test
    fun monthSummaryConvertsAndCountsWhatItCannot() {
        val day = LocalDate.of(2026, 10, 5)
        val rates = ExchangeRates(base = "USD", updatedAtEpochSeconds = 0, rates = mapOf("USD" to 1.0, "EUR" to 0.5))
        val summary =
            ExpenseCalculations.monthSummary(
                transactions = listOf(tx(10.0, day, currency = "EUR"), tx(5.0, day, currency = "JPY")),
                payments = emptyList(),
                currency = "USD",
                rates = rates,
                month = YearMonth.of(2026, 10),
            )
        assertEquals(20.0, summary.expenses, 0.001)
        assertEquals(1, summary.unconvertedCount)
    }
}
