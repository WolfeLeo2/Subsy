package com.wolfeleo2.subsy.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.data.model.BudgetPlan
import com.wolfeleo2.subsy.data.model.CategoryRef
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.model.convertTo
import com.wolfeleo2.subsy.data.model.resolveCategory
import com.wolfeleo2.subsy.data.repository.CategoryRepository
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.repository.PaymentRecordRepository
import com.wolfeleo2.subsy.data.repository.SubscriptionRepository
import com.wolfeleo2.subsy.data.repository.TransactionRepository
import com.wolfeleo2.subsy.data.util.ExpenseCalculations
import com.wolfeleo2.subsy.data.util.MonthSummary
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.data.util.today
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class TransactionFilter { ALL, OUT, IN }

/** One line in the list: a logged transaction, or a subscription payment shown alongside them. */
data class TransactionRow(
    val id: String,
    val type: TransactionType,
    val amount: Double,
    val currency: String,
    val date: LocalDate,
    val category: CategoryRef,
    /** Set for logged transactions; null for subscription payments. */
    val transaction: Transaction? = null,
    /** Set for subscription payments. */
    val subscription: Subscription? = null,
)

data class DayGroup(
    val date: LocalDate,
    val rows: List<TransactionRow>,
)

data class TransactionsUiState(
    val month: YearMonth = YearMonth.now(),
    val currency: String = "USD",
    /** The shown month's budget (its own, else the usual one) converted into [currency], if set and a rate is known. */
    val budget: Double? = null,
    val summary: MonthSummary? = null,
    val days: List<DayGroup> = emptyList(),
    val filter: TransactionFilter = TransactionFilter.ALL,
    val canGoForward: Boolean = false,
    /** False until anything at all has been logged or paid, for the first-run empty state. */
    val hasAnything: Boolean = false,
    val isLoading: Boolean = true,
)

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    paymentRecords: Flow<List<PaymentRecord>>,
    subscriptions: Flow<List<Subscription>>,
    customCategories: Flow<List<CustomCategory>>,
    primaryCurrency: Flow<String> = flowOf("USD"),
    exchangeRates: Flow<ExchangeRates?> = flowOf(null),
    budgetPlan: Flow<BudgetPlan> = flowOf(BudgetPlan()),
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow(TransactionFilter.ALL)
    private val pendingDeletes = MutableStateFlow<Set<String>>(emptySet())
    private val deleteJobs = mutableMapOf<String, Job>()

    private val data =
        combine(transactionRepository.getAll(), paymentRecords, subscriptions, customCategories) { tx, pay, subs, customs ->
            Data(tx, pay, subs.associateBy { it.id }, customs.associateBy { it.key })
        }
    private val money =
        combine(primaryCurrency, exchangeRates, budgetPlan) { currency, rates, plan -> MoneyInputs(currency, rates, plan) }

    val uiState: StateFlow<TransactionsUiState> =
        combine(data, money, month, filter, pendingDeletes) { d, (currency, rates, plan), shown, selectedFilter, pending ->
            val visible = d.transactions.filter { it.id !in pending }
            val inMonth = { date: LocalDate -> YearMonth.from(date) == shown }
            val rows =
                visible.map { it.toRow(d.customs) }.filter { inMonth(it.date) } +
                    d.payments.mapNotNull { p -> d.subscriptions[p.subscriptionId]?.let { p.toRow(it) } }.filter { inMonth(it.date) }
            val filtered =
                rows.filter {
                    when (selectedFilter) {
                        TransactionFilter.ALL -> true
                        TransactionFilter.OUT -> it.type == TransactionType.EXPENSE
                        TransactionFilter.IN -> it.type == TransactionType.INCOME
                    }
                }
            TransactionsUiState(
                month = shown,
                currency = currency,
                budget = plan.forMonth(shown)?.convertTo(currency, rates),
                summary = ExpenseCalculations.monthSummary(visible, d.payments, currency, rates, shown),
                days =
                    filtered
                        .groupBy { it.date }
                        .toSortedMap(compareByDescending { it })
                        .map { (date, list) -> DayGroup(date, list) },
                filter = selectedFilter,
                canGoForward = shown < YearMonth.from(today()),
                hasAnything = visible.isNotEmpty() || d.payments.isNotEmpty(),
                isLoading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { if (it < YearMonth.from(today())) it.plusMonths(1) else it }

    fun setFilter(value: TransactionFilter) {
        filter.value = value
    }

    /** Hides the transaction now and deletes it after [UNDO_WINDOW_MS] unless [undoDelete] runs first. */
    fun requestDelete(id: String) {
        pendingDeletes.update { it + id }
        deleteJobs[id]?.cancel()
        deleteJobs[id] =
            viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                transactionRepository.delete(id)
                deleteJobs.remove(id)
                pendingDeletes.update { it - id }
            }
    }

    fun undoDelete(id: String) {
        deleteJobs.remove(id)?.cancel()
        pendingDeletes.update { it - id }
    }

    private data class MoneyInputs(
        val currency: String,
        val rates: ExchangeRates?,
        val plan: BudgetPlan,
    )

    private data class Data(
        val transactions: List<Transaction>,
        val payments: List<PaymentRecord>,
        val subscriptions: Map<String, Subscription>,
        val customs: Map<String, CustomCategory>,
    )

    class Factory(
        private val transactionRepository: TransactionRepository,
        private val paymentRecordRepository: PaymentRecordRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val categoryRepository: CategoryRepository,
        private val primaryCurrency: Flow<String>,
        private val exchangeRates: Flow<ExchangeRates?>,
        private val budgetPlan: Flow<BudgetPlan>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TransactionsViewModel(
                transactionRepository,
                paymentRecordRepository.getAllPaymentRecords(),
                subscriptionRepository.getAllSubscriptions(),
                categoryRepository.getCustom(),
                primaryCurrency,
                exchangeRates,
                budgetPlan,
            ) as T
    }

    companion object {
        /** Matches SnackbarDuration.Long so the undo action is visible for the whole window. */
        const val UNDO_WINDOW_MS = 10_000L
    }
}

private fun Transaction.toRow(customs: Map<String, CustomCategory>) =
    TransactionRow(id, type, amount, currency, date.toLocalDate(), resolveCategory(categoryKey, type, customs), transaction = this)

private fun PaymentRecord.toRow(subscription: Subscription) =
    TransactionRow(
        id = "payment:$id",
        type = TransactionType.EXPENSE,
        amount = amount,
        currency = currency,
        date = paymentDate.toLocalDate(),
        category = CategoryRef.Builtin(SpendCategory.SUBSCRIPTIONS),
        subscription = subscription,
    )
