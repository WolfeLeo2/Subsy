package com.wolfeleo2.subsy.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.repository.SubscriptionRepository
import com.wolfeleo2.subsy.data.repository.inCurrency
import com.wolfeleo2.subsy.data.util.ExpenseCalculations
import com.wolfeleo2.subsy.data.util.topWithOverflow
import com.wolfeleo2.subsy.ui.components.CurrencyNote
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

enum class SubscriptionFilter(
    @param:StringRes val label: Int,
) {
    ALL(R.string.filter_all),
    ACTIVE(R.string.filter_active),
    TRIALS(R.string.filter_trials),
    PAUSED(R.string.filter_paused),
}

data class HomeUiState(
    /** Running trials, shown in their own group above the list. */
    val trials: List<Subscription> = emptyList(),
    /** Everything else that matches the filter, soonest charge first. */
    val subscriptions: List<Subscription> = emptyList(),
    val selectedFilter: SubscriptionFilter = SubscriptionFilter.ALL,
    val currency: String = "USD",
    val monthlyBurnRate: Double = 0.0,
    val yearlyProjected: Double = 0.0,
    val trialConversionCost: Double = 0.0,
    val activeTrialsCount: Int = 0,
    val currencyNote: CurrencyNote = CurrencyNote(),
    /** The hero's logo row: the costliest live subscriptions, then how many more are live. */
    val heroLogos: List<Subscription> = emptyList(),
    val heroMoreCount: Int = 0,
    val activeCount: Int = 0,
    /** The live subscription whose charge or trial end comes soonest (overdue ones first), if any. */
    val nextCharge: Subscription? = null,
    val hasAnySubscriptions: Boolean = false,
    val isLoading: Boolean = false,
)

class HomeViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    primaryCurrency: Flow<String> = flowOf("USD"),
    exchangeRates: Flow<ExchangeRates?> = flowOf(null),
) : ViewModel() {
    private val selectedFilterFlow = MutableStateFlow(SubscriptionFilter.ALL)

    /** Swiped away but not yet deleted, so the snackbar can undo it. */
    private val pendingDeletes = MutableStateFlow<Set<String>>(emptySet())
    private val deleteJobs = mutableMapOf<String, Job>()

    val uiState: StateFlow<HomeUiState> =
        combine(
            subscriptionRepository.getAllSubscriptions(),
            selectedFilterFlow,
            pendingDeletes,
            primaryCurrency,
            exchangeRates,
        ) { all, filter, pending, currency, rates ->
            val visible = all.filterNot { it.id in pending }
            val totals = visible.filter { it.isLive }.inCurrency(currency, rates)
            val inCurrency = totals.subscriptions
            val matching =
                when (filter) {
                    SubscriptionFilter.ALL -> visible.filter { it.status != SubscriptionStatus.CANCELLED }
                    SubscriptionFilter.ACTIVE -> visible.filter { it.status == SubscriptionStatus.ACTIVE }
                    SubscriptionFilter.TRIALS -> visible.filter { it.status == SubscriptionStatus.TRIAL }
                    SubscriptionFilter.PAUSED -> visible.filter { it.status == SubscriptionStatus.PAUSED }
                }
            val (trials, rest) = matching.partition { it.status == SubscriptionStatus.TRIAL }
            // The hero ignores the filter: it is about everything that is running.
            val live = visible.filter { it.isLive }
            val converted = inCurrency.map { it.id }.toSet()
            val ranked = inCurrency.sortedByDescending { it.monthlyCost } + live.filter { it.id !in converted }
            val (logos, more) = topWithOverflow(ranked.map { sub -> live.first { it.id == sub.id } }, HERO_LOGOS)

            HomeUiState(
                trials = trials.sortedBy { it.trialEndDate ?: it.nextBillingDate },
                subscriptions = rest,
                selectedFilter = filter,
                currency = currency,
                monthlyBurnRate = ExpenseCalculations.calculateTotalMonthlyBurnRate(inCurrency),
                yearlyProjected = ExpenseCalculations.calculateTotalYearlyProjectedCost(inCurrency),
                trialConversionCost = ExpenseCalculations.calculateTrialConversionCost(inCurrency),
                activeTrialsCount = ExpenseCalculations.calculateActiveTrialsCount(inCurrency),
                currencyNote = CurrencyNote(totals.convertedCount, totals.unconvertedCount, rates?.updatedAtEpochSeconds),
                heroLogos = logos,
                heroMoreCount = more,
                activeCount = live.count { it.status == SubscriptionStatus.ACTIVE },
                nextCharge =
                    live.minByOrNull { sub ->
                        if (sub.status == SubscriptionStatus.TRIAL) sub.trialEndDate ?: sub.nextBillingDate else sub.nextBillingDate
                    },
                hasAnySubscriptions = visible.isNotEmpty(),
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState(isLoading = true),
        )

    fun onFilterSelected(filter: SubscriptionFilter) {
        selectedFilterFlow.value = filter
    }

    fun pauseSubscription(id: String) {
        viewModelScope.launch { subscriptionRepository.pauseSubscription(id) }
    }

    fun resumeSubscription(id: String) {
        viewModelScope.launch { subscriptionRepository.resumeSubscription(id) }
    }

    /**
     * Hides the subscription now and deletes it after [UNDO_WINDOW_MS] unless [undoDelete] runs first.
     * If the screen goes away before then the delete is dropped, which loses nothing.
     */
    fun requestDelete(id: String) {
        pendingDeletes.update { it + id }
        deleteJobs[id]?.cancel()
        deleteJobs[id] =
            viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                subscriptionRepository.deleteSubscriptionById(id)
                deleteJobs.remove(id)
                pendingDeletes.update { it - id }
            }
    }

    fun undoDelete(id: String) {
        deleteJobs.remove(id)?.cancel()
        pendingDeletes.update { it - id }
    }

    class Factory(
        private val repository: SubscriptionRepository,
        private val primaryCurrency: Flow<String> = flowOf("USD"),
        private val exchangeRates: Flow<ExchangeRates?> = flowOf(null),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository, primaryCurrency, exchangeRates) as T
    }

    companion object {
        private const val HERO_LOGOS = 4

        /** Matches SnackbarDuration.Long so the undo action is visible for the whole window. */
        const val UNDO_WINDOW_MS = 10_000L
    }
}
