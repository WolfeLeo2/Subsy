package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.local.dao.SubscriptionDao
import com.wolfeleo2.subsy.data.local.entity.SubscriptionEntity
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.repository.DefaultSubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubscriptionRepositoryTest {
    private class FakeSubscriptionDao : SubscriptionDao {
        val storage = mutableMapOf<String, SubscriptionEntity>()
        private val storageFlow = MutableStateFlow<List<SubscriptionEntity>>(emptyList())

        private fun emit() {
            storageFlow.value = storage.values.toList()
        }

        /** Ids with payment records, which the real DAO reads from payment_records. */
        val paidIds = mutableSetOf<String>()

        override fun getAllSubscriptions(): Flow<List<SubscriptionEntity>> = storageFlow.map { list -> list.filter { it.archivedAt == null } }

        override fun getSubscriptionHistory(): Flow<List<SubscriptionEntity>> = storageFlow

        override suspend fun hasPayments(id: String): Boolean = id in paidIds

        override suspend fun archive(
            id: String,
            at: Long,
        ) {
            storage[id]?.let {
                storage[id] = it.copy(archivedAt = at, status = "CANCELLED")
                emit()
            }
        }

        override fun getSubscriptionById(id: String): Flow<SubscriptionEntity?> = storageFlow.map { list -> list.find { it.id == id } }

        override suspend fun getSubscriptionByIdDirect(id: String): SubscriptionEntity? = storage[id]

        override suspend fun insertSubscription(subscription: SubscriptionEntity) {
            storage[subscription.id] = subscription
            emit()
        }

        override suspend fun updateSubscription(subscription: SubscriptionEntity) {
            storage[subscription.id] = subscription
            emit()
        }

        override suspend fun deleteSubscription(subscription: SubscriptionEntity) {
            storage.remove(subscription.id)
            emit()
        }

        override suspend fun deleteSubscriptionById(id: String) {
            storage.remove(id)
            emit()
        }

        override suspend fun updateNextBillingDate(
            id: String,
            nextBillingDate: Long,
        ) {
            storage[id]?.let {
                storage[id] = it.copy(nextBillingDate = nextBillingDate)
                emit()
            }
        }

        override suspend fun updateStatus(
            id: String,
            status: String,
        ) {
            storage[id]?.let {
                storage[id] = it.copy(status = status)
                emit()
            }
        }
    }

    private lateinit var fakeDao: FakeSubscriptionDao
    private lateinit var repository: DefaultSubscriptionRepository

    @Before
    fun setup() {
        fakeDao = FakeSubscriptionDao()
        repository = DefaultSubscriptionRepository(fakeDao)
    }

    @Test
    fun insertAndRetrieveSubscription() =
        runTest {
            val subscription =
                Subscription(
                    id = "test-1",
                    name = "Netflix",
                    category = Category.ENTERTAINMENT,
                    amount = 15.49,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                )

            repository.insertSubscription(subscription)

            val direct = repository.getSubscriptionByIdDirect("test-1")
            assertNotNull(direct)
            assertEquals("Netflix", direct?.name)

            val all = repository.getAllSubscriptions().first()
            assertEquals(1, all.size)
            assertEquals("Netflix", all.first().name)
        }

    @Test
    fun pauseAndResumeSubscription() =
        runTest {
            val sub =
                Subscription(
                    id = "test-2",
                    name = "Gym",
                    category = Category.HEALTH_FITNESS,
                    amount = 40.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                )
            repository.insertSubscription(sub)

            repository.pauseSubscription("test-2")
            val paused = repository.getSubscriptionByIdDirect("test-2")
            assertEquals(SubscriptionStatus.PAUSED, paused?.status)

            repository.resumeSubscription("test-2")
            val resumed = repository.getSubscriptionByIdDirect("test-2")
            assertEquals(SubscriptionStatus.ACTIVE, resumed?.status)
        }

    @Test
    fun advanceToNextBillingCycleCalculatesCorrectDate() =
        runTest {
            val sub =
                Subscription(
                    id = "test-3",
                    name = "Cloud Storage",
                    category = Category.UTILITIES,
                    amount = 9.99,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1_000_000L,
                    nextBillingDate = 2_000_000L,
                    status = SubscriptionStatus.ACTIVE,
                )
            repository.insertSubscription(sub)

            val nextDate = repository.advanceToNextBillingCycle("test-3")
            assertNotNull(nextDate)
            assertTrue(nextDate!! > 2_000_000L)

            val updated = repository.getSubscriptionByIdDirect("test-3")
            assertEquals(nextDate, updated?.nextBillingDate)
        }

    @Test
    fun resumingTrialWithFutureEndDateRestoresTrial() =
        runTest {
            val trialEnd = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
            repository.insertSubscription(
                Subscription(
                    id = "trial-1",
                    name = "Audible",
                    category = Category.ENTERTAINMENT,
                    amount = 14.95,
                    startDate = trialEnd,
                    nextBillingDate = trialEnd,
                    isFreeTrial = true,
                    trialEndDate = trialEnd,
                ),
            )

            repository.pauseSubscription("trial-1")
            repository.resumeSubscription("trial-1")

            assertEquals(SubscriptionStatus.TRIAL, repository.getSubscriptionByIdDirect("trial-1")?.status)
        }
}
