package com.wolfeleo2.subsy.ui

import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.ui.analytics.AnalyticsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AnalyticsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var viewModel: AnalyticsViewModel

    @Before
    fun setup() {
        repository = FakeSubscriptionRepository()
        viewModel = AnalyticsViewModel(repository)
    }

    @Test
    fun computesBurnRatesAndInsightsAccurately() =
        runTest {
            val now = System.currentTimeMillis()
            repository.insertSubscription(
                Subscription(
                    id = "1",
                    name = "Netflix",
                    category = Category.ENTERTAINMENT,
                    amount = 15.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = now,
                    nextBillingDate = now + 10_000L,
                    status = SubscriptionStatus.ACTIVE,
                ),
            )
            repository.insertSubscription(
                Subscription(
                    id = "2",
                    name = "GitHub Copilot",
                    category = Category.WORK_SAAS,
                    amount = 10.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = now,
                    nextBillingDate = now + 20_000L,
                    status = SubscriptionStatus.ACTIVE,
                ),
            )
            repository.insertSubscription(
                Subscription(
                    id = "3",
                    name = "Audible Trial",
                    category = Category.ENTERTAINMENT,
                    amount = 14.95,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = now,
                    nextBillingDate = now + 50_000L,
                    isFreeTrial = true,
                    trialEndDate = now + 50_000L,
                    status = SubscriptionStatus.TRIAL,
                ),
            )

            val state = viewModel.uiState.first { it.upcomingRenewals.size == 3 }

            // Trials don't burn: 15 + 10 = 25
            assertEquals(25.0, state.monthlyBurnRate, 0.01)
            assertEquals(25.0 * 12.0, state.yearlyProjected, 0.01)

            // Trial metrics
            assertEquals(1, state.activeTrialsCount)
            assertEquals(14.95, state.trialConversionCost, 0.01)

            // Category breakdown covers ACTIVE only, largest first: ENTERTAINMENT (15), WORK_SAAS (10)
            assertEquals(listOf(Category.ENTERTAINMENT, Category.WORK_SAAS), state.categoryBreakdown.map { it.category })
            val entertainment = state.categoryBreakdown.first()
            assertEquals(15.0, entertainment.monthlyTotal, 0.01)
            assertEquals(1, entertainment.subscriptionCount)

            // Upcoming renewals sorted ascending
            assertEquals(3, state.upcomingRenewals.size)
            assertEquals("Netflix", state.upcomingRenewals[0].subscription.name)
            assertEquals("GitHub Copilot", state.upcomingRenewals[1].subscription.name)
            assertEquals("Audible Trial", state.upcomingRenewals[2].subscription.name)
        }

    @Test
    fun renewalsBeyondThirtyDaysAreExcluded() =
        runTest {
            val in40Days = System.currentTimeMillis() + 40L * 24 * 60 * 60 * 1000
            repository.insertSubscription(
                Subscription(
                    id = "far",
                    name = "Far Away",
                    category = Category.OTHER,
                    amount = 5.0,
                    startDate = in40Days,
                    nextBillingDate = in40Days,
                ),
            )

            val state = viewModel.uiState.first { it.hasAnySubscriptions }
            assertTrue(state.upcomingRenewals.isEmpty())
        }

    @Test
    fun emptyStateReturnsZeroMetrics() =
        runTest {
            val state = viewModel.uiState.first()
            assertEquals(0.0, state.monthlyBurnRate, 0.001)
            assertEquals(0.0, state.yearlyProjected, 0.001)
            assertEquals(0, state.activeTrialsCount)
            assertTrue(state.categoryBreakdown.isEmpty())
            assertTrue(state.upcomingRenewals.isEmpty())
        }

    @Test
    fun monthlySpendComesFromPaymentHistory() =
        runTest {
            val payments = FakePaymentRecordRepository()
            payments.recordPayment(
                PaymentRecord(id = "p1", subscriptionId = "1", amount = 12.0, currency = "USD", paymentDate = System.currentTimeMillis()),
            )
            repository.insertSubscription(
                Subscription(
                    id = "1",
                    name = "Netflix",
                    category = Category.ENTERTAINMENT,
                    amount = 12.0,
                    startDate = 1L,
                    nextBillingDate = System.currentTimeMillis() + 10_000L,
                ),
            )
            val vm = AnalyticsViewModel(repository, paymentRecords = payments.getAllPaymentRecords())

            val state = vm.uiState.first { !it.isLoading }
            assertEquals(12, state.monthlySpend.size)
            assertEquals(12.0, state.monthlySpend.last().total, 0.001)
            assertEquals(0.0, state.monthlySpend.first().total, 0.001)
        }
}
