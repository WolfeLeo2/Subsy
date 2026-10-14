package com.wolfeleo2.subsy.ui

import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.ui.home.HomeViewModel
import com.wolfeleo2.subsy.ui.home.HomeViewModel.Companion.UNDO_WINDOW_MS
import com.wolfeleo2.subsy.ui.home.SubscriptionFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeSubscriptionRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        repository = FakeSubscriptionRepository()
        viewModel = HomeViewModel(repository)
    }

    @Test
    fun initialStateHasZeroExpenses() =
        runTest {
            val state = viewModel.uiState.first()
            assertEquals(0.0, state.monthlyBurnRate, 0.001)
            assertEquals(0.0, state.yearlyProjected, 0.001)
            assertEquals(0, state.activeTrialsCount)
            assertTrue(state.subscriptions.isEmpty())
        }

    @Test
    fun burnRateCalculatesCorrectlyWithSubscriptions() =
        runTest {
            repository.insertSubscription(
                Subscription(
                    id = "1",
                    name = "Spotify",
                    category = Category.ENTERTAINMENT,
                    amount = 10.99,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                ),
            )
            repository.insertSubscription(
                Subscription(
                    id = "2",
                    name = "Amazon Prime",
                    category = Category.PERSONAL,
                    amount = 120.0,
                    billingCycle = BillingCycle.YEARLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                ),
            )

            val state = viewModel.uiState.first { it.subscriptions.size == 2 }
            // 10.99 + 10.0 = 20.99
            assertEquals(20.99, state.monthlyBurnRate, 0.01)
            assertEquals(20.99 * 12.0, state.yearlyProjected, 0.01)
        }

    @Test
    fun filteringSubscriptionsWorksCorrectly() =
        runTest {
            repository.insertSubscription(
                Subscription(
                    id = "1",
                    name = "Active Sub",
                    category = Category.WORK_SAAS,
                    amount = 15.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                ),
            )
            repository.insertSubscription(
                Subscription(
                    id = "2",
                    name = "Paused Sub",
                    category = Category.HEALTH_FITNESS,
                    amount = 30.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.PAUSED,
                ),
            )
            repository.insertSubscription(
                Subscription(
                    id = "3",
                    name = "Trial Sub",
                    category = Category.ENTERTAINMENT,
                    amount = 5.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    isFreeTrial = true,
                    status = SubscriptionStatus.TRIAL,
                ),
            )

            // Filter: ACTIVE
            viewModel.onFilterSelected(SubscriptionFilter.ACTIVE)
            val activeState = viewModel.uiState.first { it.selectedFilter == SubscriptionFilter.ACTIVE }
            assertEquals(1, activeState.subscriptions.size)
            assertEquals("Active Sub", activeState.subscriptions.first().name)

            // Filter: PAUSED
            viewModel.onFilterSelected(SubscriptionFilter.PAUSED)
            val pausedState = viewModel.uiState.first { it.selectedFilter == SubscriptionFilter.PAUSED }
            assertEquals(1, pausedState.subscriptions.size)
            assertEquals("Paused Sub", pausedState.subscriptions.first().name)

            // Filter: TRIALS
            viewModel.onFilterSelected(SubscriptionFilter.TRIALS)
            val trialState = viewModel.uiState.first { it.selectedFilter == SubscriptionFilter.TRIALS }
            assertTrue(trialState.subscriptions.isEmpty())
            assertEquals("Trial Sub", trialState.trials.single().name)
        }

    private fun sub(
        id: String,
        amount: Double = 10.0,
        currency: String = "USD",
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    ): Subscription =
        Subscription(
            id = id,
            name = "Sub $id",
            category = Category.OTHER,
            amount = amount,
            currency = currency,
            startDate = 1000L,
            nextBillingDate = 2000L,
            status = status,
        )

    @Test
    fun trialCostGoesToConversionCostNotBurnRate() =
        runTest {
            repository.insertSubscription(sub("a", amount = 10.0))
            repository.insertSubscription(sub("t", amount = 5.0, status = SubscriptionStatus.TRIAL))

            val state = viewModel.uiState.first { it.trials.isNotEmpty() }
            assertEquals(10.0, state.monthlyBurnRate, 0.001)
            assertEquals(5.0, state.trialConversionCost, 0.001)
        }

    @Test
    fun otherCurrencySubscriptionIsCountedButExcludedFromTotals() =
        runTest {
            repository.insertSubscription(sub("usd", amount = 10.0))
            repository.insertSubscription(sub("eur", amount = 20.0, currency = "EUR"))

            val state = viewModel.uiState.first { it.subscriptions.size == 2 }
            assertEquals(1, state.currencyNote.unconvertedCount)
            assertEquals(10.0, state.monthlyBurnRate, 0.001)
        }

    @Test
    fun otherCurrencyIsConvertedWhenRatesExist() =
        runTest {
            val rates = ExchangeRates(base = "USD", updatedAtEpochSeconds = 0, rates = mapOf("USD" to 1.0, "EUR" to 0.5))
            val converting = HomeViewModel(repository, exchangeRates = flowOf(rates))
            repository.insertSubscription(sub("usd", amount = 10.0))
            repository.insertSubscription(sub("eur", amount = 20.0, currency = "EUR"))

            val state = converting.uiState.first { it.subscriptions.size == 2 }
            assertEquals(1, state.currencyNote.convertedCount)
            assertEquals(0, state.currencyNote.unconvertedCount)
            // 20 EUR at 0.5 EUR per USD is 40 USD.
            assertEquals(50.0, state.monthlyBurnRate, 0.001)
        }

    @Test
    fun allFilterHidesCancelled() =
        runTest {
            repository.insertSubscription(sub("a"))
            repository.insertSubscription(sub("c", status = SubscriptionStatus.CANCELLED))

            val state = viewModel.uiState.first { it.hasAnySubscriptions }
            assertEquals(listOf("a"), state.subscriptions.map { it.id })
        }

    @Test
    fun requestDeleteHidesItemAndUndoRestoresIt() =
        runTest {
            repository.insertSubscription(sub("a"))

            viewModel.requestDelete("a")
            val hidden = viewModel.uiState.first { !it.isLoading && !it.hasAnySubscriptions }
            assertTrue(hidden.subscriptions.isEmpty())

            viewModel.undoDelete("a")
            val restored = viewModel.uiState.first { it.hasAnySubscriptions }
            assertEquals(listOf("a"), restored.subscriptions.map { it.id })
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun requestDeleteDeletesFromRepositoryAfterUndoWindow() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.insertSubscription(sub("a"))

            viewModel.requestDelete("a")
            advanceTimeBy(UNDO_WINDOW_MS + 1)

            assertNull(repository.getSubscriptionByIdDirect("a"))
        }

    @Test
    fun heroShowsTheCostliestLogosTheOverflowAndTheSoonestCharge() =
        runTest {
            listOf(10.0, 50.0, 30.0, 20.0, 40.0, 5.0).forEachIndexed { i, amount ->
                repository.insertSubscription(sub("s$i", amount = amount).copy(nextBillingDate = 10_000L - i * 1000L))
            }
            repository.insertSubscription(sub("paused", amount = 99.0, status = SubscriptionStatus.PAUSED))

            val state = viewModel.uiState.first { it.hasAnySubscriptions }
            // Most expensive first, paused left out, and the rest counted.
            assertEquals(listOf("s1", "s4", "s2", "s3"), state.heroLogos.map { it.id })
            assertEquals(2, state.heroMoreCount)
            assertEquals(6, state.activeCount)
            assertEquals("s5", state.nextCharge?.id)
        }

    @Test
    fun heroIgnoresTheListFilter() =
        runTest {
            repository.insertSubscription(sub("a"))
            repository.insertSubscription(sub("t", status = SubscriptionStatus.TRIAL))
            viewModel.onFilterSelected(SubscriptionFilter.PAUSED)

            val state = viewModel.uiState.first { it.hasAnySubscriptions && it.selectedFilter == SubscriptionFilter.PAUSED }
            assertEquals(setOf("a", "t"), state.heroLogos.map { it.id }.toSet())
        }
}
