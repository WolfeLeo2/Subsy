package com.wolfeleo2.subsy.data.repository

import com.wolfeleo2.subsy.data.local.dao.SubscriptionDao
import com.wolfeleo2.subsy.data.mapper.toDomain
import com.wolfeleo2.subsy.data.mapper.toEntity
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.data.util.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

interface SubscriptionRepository {
    fun getAllSubscriptions(): Flow<List<Subscription>>

    fun getSubscriptionById(id: String): Flow<Subscription?>

    suspend fun getSubscriptionByIdDirect(id: String): Subscription?

    suspend fun insertSubscription(subscription: Subscription)

    suspend fun updateSubscription(subscription: Subscription)

    suspend fun deleteSubscription(subscription: Subscription)

    suspend fun deleteSubscriptionById(id: String)

    suspend fun pauseSubscription(id: String)

    suspend fun resumeSubscription(id: String)

    suspend fun cancelSubscription(id: String)

    suspend fun advanceToNextBillingCycle(id: String): Long?
}

/** A trial resumes as a trial while its trial window is still open (ends today or later). */
fun Subscription.resumedStatus(today: LocalDate = today()): SubscriptionStatus =
    if (trialEndDate != null && !trialEndDate.toLocalDate().isBefore(today)) SubscriptionStatus.TRIAL else SubscriptionStatus.ACTIVE

/** Resumed copy: trial or active again, with a next charge that is not in the past. */
fun Subscription.resumed(now: Long = System.currentTimeMillis()): Subscription {
    val status = resumedStatus()
    val next =
        if (status == SubscriptionStatus.ACTIVE && nextBillingDate.toLocalDate().isBefore(today())) {
            billingCycle.nextBillingAfter(startDate, now)
        } else {
            nextBillingDate
        }
    return copy(status = status, nextBillingDate = next)
}

fun Subscription.nextBillingAfterCurrent(): Long = billingCycle.nextBillingAfter(startDate, nextBillingDate)

class DefaultSubscriptionRepository(
    private val subscriptionDao: SubscriptionDao,
) : SubscriptionRepository {
    override fun getAllSubscriptions(): Flow<List<Subscription>> =
        subscriptionDao.getAllSubscriptions().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getSubscriptionById(id: String): Flow<Subscription?> = subscriptionDao.getSubscriptionById(id).map { it?.toDomain() }

    override suspend fun getSubscriptionByIdDirect(id: String): Subscription? = subscriptionDao.getSubscriptionByIdDirect(id)?.toDomain()

    override suspend fun insertSubscription(subscription: Subscription) {
        subscriptionDao.insertSubscription(subscription.toEntity())
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        subscriptionDao.updateSubscription(subscription.toEntity())
    }

    override suspend fun deleteSubscription(subscription: Subscription) {
        subscriptionDao.deleteSubscription(subscription.toEntity())
    }

    override suspend fun deleteSubscriptionById(id: String) {
        subscriptionDao.deleteSubscriptionById(id)
    }

    override suspend fun pauseSubscription(id: String) {
        subscriptionDao.updateStatus(id, SubscriptionStatus.PAUSED.name)
    }

    override suspend fun resumeSubscription(id: String) {
        val existing = getSubscriptionByIdDirect(id) ?: return
        subscriptionDao.updateSubscription(existing.resumed().toEntity())
    }

    override suspend fun cancelSubscription(id: String) {
        subscriptionDao.updateStatus(id, SubscriptionStatus.CANCELLED.name)
    }

    override suspend fun advanceToNextBillingCycle(id: String): Long? {
        val existing = getSubscriptionByIdDirect(id) ?: return null
        val nextDate = existing.nextBillingAfterCurrent()
        subscriptionDao.updateNextBillingDate(id, nextDate)
        return nextDate
    }
}
