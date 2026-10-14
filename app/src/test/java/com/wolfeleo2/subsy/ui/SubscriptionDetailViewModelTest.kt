package com.wolfeleo2.subsy.ui

import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.ui.detail.DetailEvent
import com.wolfeleo2.subsy.ui.detail.SubscriptionDetailViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SubscriptionDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var subRepository: FakeSubscriptionRepository
    private lateinit var paymentRepository: FakePaymentRecordRepository

    @Before
    fun setup() {
        subRepository = FakeSubscriptionRepository()
        paymentRepository = FakePaymentRecordRepository()
    }

    @Test
    fun loadsSubscriptionAndPaymentHistory() =
        runTest {
            val sub =
                Subscription(
                    id = "sub-1",
                    name = "YouTube Premium",
                    category = Category.ENTERTAINMENT,
                    amount = 13.99,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                )
            subRepository.insertSubscription(sub)
            paymentRepository.recordPayment(
                PaymentRecord(
                    id = "p-1",
                    subscriptionId = "sub-1",
                    amount = 13.99,
                    currency = "USD",
                    paymentDate = 1000L,
                    status = PaymentStatus.MANUALLY_LOGGED,
                ),
            )

            val viewModel = SubscriptionDetailViewModel("sub-1", subRepository, paymentRepository)
            val state = viewModel.uiState.first { it.subscription != null }

            assertEquals("YouTube Premium", state.subscription?.name)
            assertEquals(1, state.paymentHistory.size)
            assertEquals(13.99, state.totalSpent, 0.001)
        }

    @Test
    fun pauseAndResumeSubscriptionUpdatesState() =
        runTest {
            val sub =
                Subscription(
                    id = "sub-2",
                    name = "Gym Membership",
                    category = Category.HEALTH_FITNESS,
                    amount = 50.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                )
            subRepository.insertSubscription(sub)

            val viewModel = SubscriptionDetailViewModel("sub-2", subRepository, paymentRepository)

            viewModel.pauseSubscription()
            val paused = subRepository.getSubscriptionByIdDirect("sub-2")
            assertEquals(SubscriptionStatus.PAUSED, paused?.status)

            viewModel.resumeSubscription()
            val resumed = subRepository.getSubscriptionByIdDirect("sub-2")
            assertEquals(SubscriptionStatus.ACTIVE, resumed?.status)
        }

    @Test
    fun logPaymentAndAdvanceCycleRecordsPaymentAndUpdatesDate() =
        runTest {
            val sub =
                Subscription(
                    id = "sub-3",
                    name = "Claude Pro",
                    category = Category.WORK_SAAS,
                    amount = 20.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1_000_000L,
                    nextBillingDate = 2_000_000L,
                    status = SubscriptionStatus.ACTIVE,
                )
            subRepository.insertSubscription(sub)

            val viewModel = SubscriptionDetailViewModel("sub-3", subRepository, paymentRepository)
            viewModel.logPaymentAndAdvanceCycle()

            val history = paymentRepository.getPaymentRecordsForSubscription("sub-3").first()
            assertEquals(1, history.size)
            assertEquals(20.0, history.first().amount, 0.001)

            val updated = subRepository.getSubscriptionByIdDirect("sub-3")
            assertNotNull(updated)
            assertTrue((updated?.nextBillingDate ?: 0L) > 2_000_000L)
        }

    @Test
    fun loggingPaymentOnTrialConvertsItToActive() =
        runTest {
            subRepository.insertSubscription(
                Subscription(
                    id = "trial-1",
                    name = "Audible",
                    category = Category.ENTERTAINMENT,
                    amount = 14.95,
                    startDate = 1_000_000L,
                    nextBillingDate = 2_000_000L,
                    isFreeTrial = true,
                    trialEndDate = 2_000_000L,
                ),
            )

            SubscriptionDetailViewModel("trial-1", subRepository, paymentRepository).logPaymentAndAdvanceCycle()

            assertEquals(SubscriptionStatus.ACTIVE, subRepository.getSubscriptionByIdDirect("trial-1")?.status)
        }

    @Test
    fun deleteSubscriptionRemovesAndEmitsDeletedEvent() =
        runTest(mainDispatcherRule.testDispatcher) {
            val sub =
                Subscription(
                    id = "sub-4",
                    name = "Obsolete Service",
                    category = Category.UTILITIES,
                    amount = 5.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                )
            subRepository.insertSubscription(sub)

            val viewModel = SubscriptionDetailViewModel("sub-4", subRepository, paymentRepository)

            var deletedEventReceived = false
            backgroundScope.launch {
                viewModel.eventFlow.collect { event ->
                    if (event is DetailEvent.Deleted) {
                        deletedEventReceived = true
                    }
                }
            }

            viewModel.deleteSubscription()

            assertTrue(deletedEventReceived)
            assertNull(subRepository.getSubscriptionByIdDirect("sub-4"))
        }
}
