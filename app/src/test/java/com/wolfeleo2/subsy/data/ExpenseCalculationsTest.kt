package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.BillingCycleType
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.util.ExpenseCalculations
import com.wolfeleo2.subsy.data.util.dailyAllowance
import com.wolfeleo2.subsy.data.util.monthsCovered
import com.wolfeleo2.subsy.data.util.toEpochMillis
import com.wolfeleo2.subsy.data.util.topWithOverflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpenseCalculationsTest {
    private fun createSubscription(
        id: String,
        amount: Double,
        billingCycle: BillingCycle = BillingCycle.MONTHLY,
        category: Category = Category.ENTERTAINMENT,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        isFreeTrial: Boolean = false,
        trialEndDate: Long? = null,
    ): Subscription =
        Subscription(
            id = id,
            name = "Test $id",
            category = category,
            amount = amount,
            billingCycle = billingCycle,
            startDate = 1000L,
            nextBillingDate = 2000L,
            isFreeTrial = isFreeTrial,
            trialEndDate = trialEndDate,
            status = status,
        )

    @Test
    fun totalMonthlyBurnRateCalculatesCorrectly() {
        val list =
            listOf(
                // Monthly $20 -> $20
                createSubscription("1", 20.0, BillingCycle.MONTHLY, status = SubscriptionStatus.ACTIVE),
                // Yearly $120 -> $10
                createSubscription("2", 120.0, BillingCycle.YEARLY, status = SubscriptionStatus.ACTIVE),
                // Paused Monthly $50 -> $0
                createSubscription("3", 50.0, BillingCycle.MONTHLY, status = SubscriptionStatus.PAUSED),
                // Cancelled Monthly $15 -> $0
                createSubscription("4", 15.0, BillingCycle.MONTHLY, status = SubscriptionStatus.CANCELLED),
                // Trial with $10 monthly rate -> $0 until it converts
                createSubscription("5", 10.0, BillingCycle.MONTHLY, status = SubscriptionStatus.TRIAL, isFreeTrial = true),
            )

        // Expected active total = 20 + 10 = $30
        val burnRate = ExpenseCalculations.calculateTotalMonthlyBurnRate(list)
        assertEquals(30.0, burnRate, 0.001)

        val yearlyCost = ExpenseCalculations.calculateTotalYearlyProjectedCost(list)
        assertEquals(360.0, yearlyCost, 0.001)
    }

    @Test
    fun trialIsExcludedFromBurnRate() {
        val trial = createSubscription("t", 12.0, status = SubscriptionStatus.TRIAL, isFreeTrial = true)
        assertEquals(0.0, ExpenseCalculations.calculateTotalMonthlyBurnRate(listOf(trial)), 0.001)
    }

    @Test
    fun trialIsCountedInConversionCost() {
        val list =
            listOf(
                createSubscription("a", 20.0),
                createSubscription("t", 12.0, status = SubscriptionStatus.TRIAL, isFreeTrial = true),
            )
        assertEquals(12.0, ExpenseCalculations.calculateTrialConversionCost(list), 0.001)
    }

    @Test
    fun categoryBreakdownSumsActiveSubscriptionsLargestFirst() {
        val list =
            listOf(
                createSubscription("1", 30.0, category = Category.ENTERTAINMENT),
                createSubscription("2", 20.0, category = Category.ENTERTAINMENT),
                createSubscription("3", 25.0, category = Category.WORK_SAAS),
                createSubscription("4", 99.0, category = Category.HEALTH_FITNESS, status = SubscriptionStatus.TRIAL, isFreeTrial = true),
            )

        val breakdown = ExpenseCalculations.calculateCategoryBreakdown(list)
        assertEquals(listOf(Category.ENTERTAINMENT, Category.WORK_SAAS), breakdown.map { it.category })
        assertEquals(50.0, breakdown[0].monthlyTotal, 0.001)
        assertEquals(2, breakdown[0].subscriptionCount)
        assertEquals(2f / 3f, breakdown[0].share, 0.001f)
    }

    private fun trialEnding(
        id: String,
        date: LocalDate,
        status: SubscriptionStatus = SubscriptionStatus.TRIAL,
    ) = createSubscription(id, 10.0, status = status, isFreeTrial = true, trialEndDate = date.toEpochMillis())

    @Test
    fun expiringWindowIncludesTodayThroughThreeDays() {
        val today = LocalDate.of(2026, 10, 13)
        val list =
            listOf(
                trialEnding("today", today),
                trialEnding("in3", today.plusDays(3)),
                trialEnding("in4", today.plusDays(4)),
                trialEnding("yesterday", today.minusDays(1)),
            )

        val expiring = ExpenseCalculations.calculateExpiringSoonTrials(list, withinDays = 3, today = today)
        assertEquals(listOf("today", "in3"), expiring.map { it.id })
    }

    @Test
    fun expiringWindowExcludesPausedTrial() {
        val today = LocalDate.of(2026, 10, 13)
        val paused = trialEnding("paused", today.plusDays(1), SubscriptionStatus.PAUSED)
        assertTrue(ExpenseCalculations.calculateExpiringSoonTrials(listOf(paused), today = today).isEmpty())
    }

    private fun payment(
        date: LocalDate,
        amount: Double,
        currency: String = "USD",
    ) = PaymentRecord(
        id = "$date$amount$currency",
        subscriptionId = "s",
        amount = amount,
        currency = currency,
        paymentDate = date.toEpochMillis(),
    )

    @Test
    fun monthlyTotalsBucketsByMonthAndZeroFillsTheWindow() {
        val today = LocalDate.of(2026, 10, 13)
        val result =
            ExpenseCalculations.monthlyTotals(
                records =
                    listOf(
                        payment(LocalDate.of(2026, 10, 1), 10.0),
                        payment(LocalDate.of(2026, 10, 12), 5.0),
                        payment(LocalDate.of(2026, 9, 30), 7.0),
                        payment(LocalDate.of(2025, 10, 31), 99.0), // 12 months back: outside the window
                    ),
                currency = "USD",
                rates = null,
                months = 3,
                today = today,
            )
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), result.months.map { it.month })
        assertEquals(listOf(0.0, 7.0, 15.0), result.months.map { it.total })
        assertEquals(0, result.unconvertedCount)
    }

    @Test
    fun calendarYearTotalsBucketsMonths1To12AndZeroFillsFutureMonths() {
        val today = LocalDate.of(2026, 10, 13)
        val result =
            ExpenseCalculations.calendarYearTotals(
                records =
                    listOf(
                        payment(LocalDate.of(2026, 1, 15), 12.0),
                        payment(LocalDate.of(2026, 5, 20), 25.0),
                        payment(LocalDate.of(2026, 10, 5), 10.0),
                        payment(LocalDate.of(2025, 10, 5), 99.0),
                    ),
                currency = "USD",
                rates = null,
                year = today.year,
            )
        assertEquals(12, result.months.size)
        assertEquals(YearMonth.of(2026, 1), result.months.first().month)
        assertEquals(YearMonth.of(2026, 12), result.months.last().month)
        assertEquals(12.0, result.months[0].total, 0.001)
        assertEquals(0.0, result.months[1].total, 0.001)
        assertEquals(25.0, result.months[4].total, 0.001)
        assertEquals(10.0, result.months[9].total, 0.001)
        assertEquals(0.0, result.months[10].total, 0.001)
        assertEquals(0.0, result.months[11].total, 0.001)
        assertEquals(0, result.unconvertedCount)
    }

    @Test
    fun monthlyTotalsConvertsAndCountsPaymentsWithoutARate() {
        val today = LocalDate.of(2026, 10, 13)
        val rates = ExchangeRates(base = "USD", updatedAtEpochSeconds = 0, rates = mapOf("USD" to 1.0, "EUR" to 0.5))
        val records = listOf(payment(today, 10.0, "EUR"), payment(today, 4.0, "JPY"))
        val converted = ExpenseCalculations.monthlyTotals(records, "USD", rates, months = 1, today = today)
        assertEquals(20.0, converted.months.single().total, 0.001)
        assertEquals(1, converted.unconvertedCount)
        val noRates = ExpenseCalculations.monthlyTotals(records, "USD", null, months = 1, today = today)
        assertEquals(0.0, noRates.months.single().total, 0.001)
        assertEquals(2, noRates.unconvertedCount)
    }

    @Test
    fun categorySharesSumToOne() {
        val result =
            ExpenseCalculations.calculateCategoryBreakdown(
                listOf(
                    createSubscription("a", 10.0, category = Category.ENTERTAINMENT),
                    createSubscription("b", 5.0, category = Category.WORK_SAAS),
                    createSubscription("c", 1.0, category = Category.OTHER),
                ),
            )
        assertEquals(1.0, result.sumOf { it.share.toDouble() }, 0.0001)
    }

    @Test
    fun monthsCoveredFollowsTheBillingCycle() {
        assertEquals(1, BillingCycle(BillingCycleType.MONTHLY).monthsCovered())
        assertEquals(1, BillingCycle(BillingCycleType.WEEKLY).monthsCovered())
        assertEquals(3, BillingCycle(BillingCycleType.QUARTERLY).monthsCovered())
        assertEquals(12, BillingCycle(BillingCycleType.YEARLY).monthsCovered())
        assertEquals(2, BillingCycle(BillingCycleType.CUSTOM, customDays = 60).monthsCovered())
    }

    @Test
    fun monthSummarySpreadsAQuarterlyPaymentOverItsThreeMonths() {
        val paid = PaymentRecord("p", "q", 30.0, "USD", LocalDate.of(2026, 8, 15).toEpochMillis())
        val spread = mapOf("q" to 3)
        val totals =
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 10), YearMonth.of(2026, 11)).map {
                ExpenseCalculations.monthSummary(emptyList(), listOf(paid), "USD", null, it, spread).expenses
            }
        assertEquals(listOf(10.0, 10.0, 0.0), totals)
    }

    @Test
    fun rentCountsUnderItsChosenCategoryAndOthersUnderSubscriptions() {
        val month = YearMonth.of(2026, 10)
        val day = LocalDate.of(2026, 10, 5).toEpochMillis()
        val rent = PaymentRecord("r", "rent", 25000.0, "KES", day)
        val netflix = PaymentRecord("n", "netflix", 1100.0, "KES", day)
        val summary =
            ExpenseCalculations.monthSummary(
                emptyList(),
                listOf(rent, netflix),
                "KES",
                null,
                month,
                paymentCategories =
                    mapOf(
                        "rent" to "HOUSING",
                    ),
            )
        val byKey = summary.expensesByCategory.associate { it.key to it.total }
        assertEquals(25000.0, byKey.getValue("HOUSING"), 0.0)
        assertEquals(1100.0, byKey.getValue("SUBSCRIPTIONS"), 0.0)
    }

    @Test
    fun dailyAllowanceSplitsWhatIsLeftOverTheRemainingDays() {
        val october = YearMonth.of(2026, 10)
        // 13th to 31st inclusive is 19 days.
        val allowance = dailyAllowance(1900.0, october, LocalDate.of(2026, 10, 13))!!
        assertEquals(19, allowance.days)
        assertEquals(100.0, allowance.perDay, 0.001)
        assertEquals(1, dailyAllowance(50.0, october, LocalDate.of(2026, 10, 31))!!.days)
        // Only for the current month, and only while something is left.
        assertNull(dailyAllowance(1900.0, october.minusMonths(1), LocalDate.of(2026, 10, 13)))
        assertNull(dailyAllowance(0.0, october, LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun topWithOverflowCountsWhatIsLeftOut() {
        assertEquals(listOf(1, 2, 3, 4) to 2, topWithOverflow(listOf(1, 2, 3, 4, 5, 6), 4))
        assertEquals(listOf(1, 2) to 0, topWithOverflow(listOf(1, 2), 4))
    }
}
