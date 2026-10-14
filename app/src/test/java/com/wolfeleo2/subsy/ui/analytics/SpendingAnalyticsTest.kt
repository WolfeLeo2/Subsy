package com.wolfeleo2.subsy.ui.analytics

import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.BudgetPlan
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.CategoryRef
import com.wolfeleo2.subsy.data.model.Money
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.util.toEpochMillis
import com.wolfeleo2.subsy.data.util.today
import com.wolfeleo2.subsy.ui.FakeBudgetRepository
import com.wolfeleo2.subsy.ui.FakeSubscriptionRepository
import com.wolfeleo2.subsy.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class SpendingAnalyticsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val subscriptions = FakeSubscriptionRepository()
    private val budgets = FakeBudgetRepository()
    private val now = today()

    private fun tx(
        amount: Double,
        key: String,
        type: TransactionType = TransactionType.EXPENSE,
        monthsAgo: Long = 0,
    ) = Transaction("$amount$key$monthsAgo", type, amount, "USD", now.minusMonths(monthsAgo).toEpochMillis(), key)

    private fun viewModel(
        mode: AppMode,
        transactions: List<Transaction>,
        payments: List<PaymentRecord> = emptyList(),
    ) = AnalyticsViewModel(
        subscriptionRepository = subscriptions,
        paymentRecords = flowOf(payments),
        appMode = flowOf(mode),
        transactions = flowOf(transactions),
        budgets = budgets,
    )

    @Test
    fun subscriptionsModeHasNoSpendingSection() =
        runTest {
            val state = viewModel(AppMode.SUBSCRIPTIONS, listOf(tx(10.0, "DINING"))).uiState.first { !it.isLoading }
            assertNull(state.spending)
        }

    @Test
    fun allSpendingSummarizesThisMonthWithBudgetsAndHistory() =
        runTest {
            budgets.budgets.value = mapOf("DINING" to Money(50.0, "USD"), "TRAVEL" to Money(200.0, "USD"))
            subscriptions.insertSubscription(
                Subscription("s", "Netflix", Category.ENTERTAINMENT, 15.0, startDate = 1L, nextBillingDate = 2L),
            )
            val state =
                viewModel(
                    AppMode.ALL_SPENDING,
                    listOf(
                        tx(60.0, "DINING"),
                        tx(20.0, "GROCERIES"),
                        tx(2000.0, "SALARY", TransactionType.INCOME),
                        tx(99.0, "DINING", monthsAgo = 1),
                    ),
                    payments = listOf(PaymentRecord("p", "s", 15.0, "USD", now.toEpochMillis())),
                ).uiState.first { it.spending != null }
            val spending = state.spending!!

            assertEquals(2000.0, spending.income, 0.001)
            assertEquals(95.0, spending.expenses, 0.001)
            // Largest first, then a budgeted category with nothing spent.
            assertEquals(listOf("DINING", "GROCERIES", SpendCategory.SUBSCRIPTIONS.name, "TRAVEL"), spending.slices.map { it.category.key })
            assertEquals(50.0, spending.slices.first().budget!!, 0.001)
            assertEquals(0.0, spending.slices.last().total, 0.001)
            assertEquals(CategoryRef.Builtin(SpendCategory.SUBSCRIPTIONS), spending.slices[2].category)

            assertEquals(12, spending.months.size)
            assertEquals(YearMonth.from(now), spending.months.last().month)
            assertEquals(99.0, spending.months[10].expenses, 0.001)
        }

    @Test
    fun allSpendingCountsTransactionsAsSomethingToShow() =
        runTest {
            val state = viewModel(AppMode.ALL_SPENDING, listOf(tx(5.0, "OTHER"))).uiState.first { !it.isLoading }
            assertTrue(state.hasAnySubscriptions)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setCategoryBudgetSavesAndRemoves() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel(AppMode.ALL_SPENDING, emptyList())
            vm.setCategoryBudget("DINING", 80.0)
            advanceUntilIdle()
            // Stored in the primary currency it was entered in.
            assertEquals(mapOf("DINING" to Money(80.0, "USD")), budgets.budgets.value)
            vm.setCategoryBudget("DINING", null)
            advanceUntilIdle()
            assertTrue(budgets.budgets.value.isEmpty())
        }

    @Test
    fun budgetsConvertIntoThePrimaryCurrencyAndWaitWithoutARate() =
        runTest {
            val rates = ExchangeRates(base = "USD", updatedAtEpochSeconds = 0, rates = mapOf("USD" to 1.0, "GBP" to 0.5))
            budgets.budgets.value = mapOf("DINING" to Money(50.0, "GBP"), "TRAVEL" to Money(1000.0, "JPY"))
            val vm =
                AnalyticsViewModel(
                    subscriptionRepository = subscriptions,
                    primaryCurrency = flowOf("USD"),
                    exchangeRates = flowOf(rates),
                    budgetPlan = flowOf(BudgetPlan(Money(100.0, "GBP"))),
                    appMode = flowOf(AppMode.ALL_SPENDING),
                    transactions = flowOf(listOf(tx(10.0, "DINING"))),
                    budgets = budgets,
                )
            val state = vm.uiState.first { it.spending != null }
            // £100 at 0.5 GBP per USD is $200; switching back to GBP would give exactly £100 again.
            assertEquals(200.0, state.budget!!, 0.001)
            assertNull(state.budgetWaitingForRate)
            assertEquals(
                100.0,
                state.spending!!
                    .slices
                    .single { it.category.key == "DINING" }
                    .budget!!,
                0.001,
            )
            // No JPY rate: that budget is left out instead of read as dollars.
            assertTrue(state.spending.slices.none { it.category.key == "TRAVEL" })
        }

    @Test
    fun overallBudgetWithoutARateIsFlaggedNotMisread() =
        runTest {
            val vm =
                AnalyticsViewModel(
                    subscriptionRepository = subscriptions,
                    primaryCurrency = flowOf("USD"),
                    budgetPlan = flowOf(BudgetPlan(Money(100.0, "GBP"))),
                )
            val state = vm.uiState.first { !it.isLoading }
            assertNull(state.budget)
            assertEquals("GBP", state.budgetWaitingForRate)
        }

    @Test
    fun aMonthsOwnBudgetReplacesTheUsualOneThere() =
        runTest {
            val thisMonth = YearMonth.from(now)
            val plan = BudgetPlan(Money(100.0, "USD"), mapOf(thisMonth to Money(250.0, "USD")))
            val state =
                AnalyticsViewModel(
                    subscriptionRepository = subscriptions,
                    budgetPlan = flowOf(plan),
                ).uiState.first { !it.isLoading }
            assertEquals(250.0, state.budget!!, 0.001)
            assertEquals(100.0, state.defaultBudget!!, 0.001)
            assertEquals(250.0, state.budgetFor(thisMonth)!!, 0.001)
            assertEquals(100.0, state.budgetFor(thisMonth.minusMonths(1))!!, 0.001)
        }

    @Test
    fun spreadingAYearlyChargeSplitsItAcrossTheYear() =
        runTest {
            subscriptions.insertSubscription(
                Subscription(
                    "y",
                    "Yearly",
                    Category.OTHER,
                    120.0,
                    billingCycle =
                        com.wolfeleo2.subsy.data.model
                            .BillingCycle(com.wolfeleo2.subsy.data.model.BillingCycleType.YEARLY),
                    startDate = 1L,
                    nextBillingDate = 2L,
                ),
            )
            val payment = PaymentRecord("p", "y", 120.0, "USD", now.minusMonths(2).toEpochMillis())
            val spread =
                AnalyticsViewModel(
                    subscriptionRepository = subscriptions,
                    paymentRecords = flowOf(listOf(payment)),
                    spreadLongCharges = flowOf(true),
                ).uiState.first { !it.isLoading }
            // 10 a month from the month it was paid, so the last three months each carry a share.
            assertEquals(listOf(10.0, 10.0, 10.0), spread.monthlySpend.takeLast(3).map { it.total })

            val lumped =
                AnalyticsViewModel(subscriptionRepository = subscriptions, paymentRecords = flowOf(listOf(payment)))
                    .uiState
                    .first { !it.isLoading }
            assertEquals(listOf(120.0, 0.0, 0.0), lumped.monthlySpend.takeLast(3).map { it.total })
        }
}
