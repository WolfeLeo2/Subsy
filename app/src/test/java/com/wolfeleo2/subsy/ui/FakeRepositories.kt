package com.wolfeleo2.subsy.ui

import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.Money
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.repository.BudgetRepository
import com.wolfeleo2.subsy.data.repository.CategoryRepository
import com.wolfeleo2.subsy.data.repository.PaymentRecordRepository
import com.wolfeleo2.subsy.data.repository.SubscriptionRepository
import com.wolfeleo2.subsy.data.repository.TransactionRepository
import com.wolfeleo2.subsy.data.repository.nextBillingAfterCurrent
import com.wolfeleo2.subsy.data.repository.resumed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSubscriptionRepository : SubscriptionRepository {
    private val subscriptionsMap = mutableMapOf<String, Subscription>()
    private val subscriptionsFlow = MutableStateFlow<List<Subscription>>(emptyList())

    private fun emit() {
        subscriptionsFlow.value = subscriptionsMap.values.toList()
    }

    override fun getAllSubscriptions(): Flow<List<Subscription>> = subscriptionsFlow

    override fun getSubscriptionById(id: String): Flow<Subscription?> = subscriptionsFlow.map { list -> list.find { it.id == id } }

    override suspend fun getSubscriptionByIdDirect(id: String): Subscription? = subscriptionsMap[id]

    override suspend fun insertSubscription(subscription: Subscription) {
        subscriptionsMap[subscription.id] = subscription
        emit()
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        subscriptionsMap[subscription.id] = subscription
        emit()
    }

    override suspend fun deleteSubscription(subscription: Subscription) {
        subscriptionsMap.remove(subscription.id)
        emit()
    }

    override suspend fun deleteSubscriptionById(id: String) {
        subscriptionsMap.remove(id)
        emit()
    }

    override suspend fun pauseSubscription(id: String) {
        subscriptionsMap[id]?.let {
            subscriptionsMap[id] = it.copy(status = SubscriptionStatus.PAUSED)
            emit()
        }
    }

    override suspend fun resumeSubscription(id: String) {
        subscriptionsMap[id]?.let {
            subscriptionsMap[id] = it.resumed()
            emit()
        }
    }

    override suspend fun cancelSubscription(id: String) {
        subscriptionsMap[id]?.let {
            subscriptionsMap[id] = it.copy(status = SubscriptionStatus.CANCELLED)
            emit()
        }
    }

    override suspend fun advanceToNextBillingCycle(id: String): Long? {
        val existing = subscriptionsMap[id] ?: return null
        val nextDate = existing.nextBillingAfterCurrent()
        subscriptionsMap[id] = existing.copy(nextBillingDate = nextDate)
        emit()
        return nextDate
    }
}

class FakePaymentRecordRepository : PaymentRecordRepository {
    private val recordsMap = mutableMapOf<String, PaymentRecord>()
    private val recordsFlow = MutableStateFlow<List<PaymentRecord>>(emptyList())

    private fun emit() {
        recordsFlow.value = recordsMap.values.toList()
    }

    override fun getPaymentRecordsForSubscription(subscriptionId: String): Flow<List<PaymentRecord>> =
        recordsFlow.map { list -> list.filter { it.subscriptionId == subscriptionId } }

    override fun getAllPaymentRecords(): Flow<List<PaymentRecord>> = recordsFlow

    override fun getPaymentRecordsBetween(
        startDate: Long,
        endDate: Long,
    ): Flow<List<PaymentRecord>> = recordsFlow.map { list -> list.filter { it.paymentDate in startDate..endDate } }

    override fun getTotalSpentForSubscription(subscriptionId: String): Flow<Double> =
        getPaymentRecordsForSubscription(subscriptionId).map { list ->
            list.sumOf { it.amount }
        }

    override fun getTotalSpentAllTime(): Flow<Double> = recordsFlow.map { list -> list.sumOf { it.amount } }

    override suspend fun recordPayment(paymentRecord: PaymentRecord) {
        recordsMap[paymentRecord.id] = paymentRecord
        emit()
    }

    override suspend fun deletePayment(paymentRecord: PaymentRecord) {
        recordsMap.remove(paymentRecord.id)
        emit()
    }

    override suspend fun deletePaymentRecordsForSubscription(subscriptionId: String) {
        val toRemove = recordsMap.values.filter { it.subscriptionId == subscriptionId }.map { it.id }
        toRemove.forEach { recordsMap.remove(it) }
        emit()
    }
}

class FakeTransactionRepository : TransactionRepository {
    private val items = MutableStateFlow<List<Transaction>>(emptyList())

    override fun getAll(): Flow<List<Transaction>> = items.map { list -> list.sortedByDescending { it.date } }

    override suspend fun getById(id: String): Transaction? = items.value.find { it.id == id }

    override suspend fun insert(transaction: Transaction) {
        items.value += transaction
    }

    override suspend fun update(transaction: Transaction) {
        items.value = items.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun delete(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun rememberedCategory(
        merchant: String,
        type: TransactionType,
    ): String? =
        items.value
            .filter { it.merchant.equals(merchant, ignoreCase = true) && it.type == type }
            .maxByOrNull { it.date }
            ?.categoryKey
}

class FakeCategoryRepository : CategoryRepository {
    val items = MutableStateFlow<List<CustomCategory>>(emptyList())

    override fun getCustom(): Flow<List<CustomCategory>> = items

    override suspend fun add(category: CustomCategory) {
        items.value += category
    }

    override suspend fun update(category: CustomCategory) {
        items.value = items.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(category: CustomCategory) {
        items.value = items.value.filterNot { it.id == category.id }
    }
}

class FakeBudgetRepository : BudgetRepository {
    val budgets = MutableStateFlow<Map<String, Money>>(emptyMap())

    override fun getCategoryBudgets(): Flow<Map<String, Money>> = budgets

    override suspend fun setCategoryBudget(
        key: String,
        budget: Money?,
    ) {
        budgets.value = if (budget == null) budgets.value - key else budgets.value + (key to budget)
    }

    override suspend fun pinUnsetCurrency(currency: String) = Unit
}
