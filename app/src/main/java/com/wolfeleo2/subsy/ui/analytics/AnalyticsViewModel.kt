package com.wolfeleo2.subsy.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.BudgetPlan
import com.wolfeleo2.subsy.data.model.CategoryRef
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.Money
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.model.convertTo
import com.wolfeleo2.subsy.data.model.resolveCategory
import com.wolfeleo2.subsy.data.repository.BudgetRepository
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.repository.SubscriptionRepository
import com.wolfeleo2.subsy.data.repository.inCurrency
import com.wolfeleo2.subsy.data.util.CategorySpend
import com.wolfeleo2.subsy.data.util.ExpenseCalculations
import com.wolfeleo2.subsy.data.util.MonthSpend
import com.wolfeleo2.subsy.data.util.spreadMap
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.data.util.today
import com.wolfeleo2.subsy.ui.components.CurrencyNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class UpcomingRenewalItem(
    val subscription: Subscription,
    val daysRemaining: Long,
)

data class AnalyticsUiState(
    val currency: String = "USD",
    val monthlyBurnRate: Double = 0.0,
    val yearlyProjected: Double = 0.0,
    val trialConversionCost: Double = 0.0,
    val activeTrialsCount: Int = 0,
    val currencyNote: CurrencyNote = CurrencyNote(),
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    /** Charges in the next [RENEWAL_WINDOW_DAYS] days, soonest first. */
    val upcomingRenewals: List<UpcomingRenewalItem> = emptyList(),
    /** Payments per month, last 12 months, oldest first. */
    val monthlySpend: List<MonthSpend> = emptyList(),
    /** Payments per month for the 12 calendar months (Jan to Dec) of the current year. */
    val calendarYearSpend: List<MonthSpend> = emptyList(),
    /** Payments left out of [monthlySpend] because no exchange rate was available. */
    val unconvertedPayments: Int = 0,
    /** This month's budget (its override, else the usual one) converted into [currency], if set and a rate is known. */
    val budget: Double? = null,
    /** The usual monthly budget converted into [currency]: the chart's dashed line. */
    val defaultBudget: Double? = null,
    /** Budgets set for single months, converted into [currency]. */
    val monthBudgets: Map<YearMonth, Double> = emptyMap(),
    /** Currency of a budget that is set but can't be converted yet (no exchange rate); null otherwise. */
    val budgetWaitingForRate: String? = null,
    /** Set in All spending mode: this month's money in and out, by category, plus 12 months of history. */
    val spending: SpendingAnalytics? = null,
    val hasAnySubscriptions: Boolean = false,
    val isLoading: Boolean = false,
) {
    /** The budget that applies in [month]: its own, else the usual one. */
    fun budgetFor(month: YearMonth): Double? = monthBudgets[month] ?: defaultBudget
}

/** One category's spending this month, with its budget if one is set. */
data class SpendingSlice(
    val category: CategoryRef,
    val total: Double,
    val budget: Double?,
)

data class MonthInOut(
    val month: YearMonth,
    val income: Double,
    val expenses: Double,
)

data class SpendingAnalytics(
    val income: Double,
    val expenses: Double,
    /** This month's spending by category, largest first, then budgeted categories with nothing spent yet. */
    val slices: List<SpendingSlice>,
    /** The last 12 months, oldest first. */
    val months: List<MonthInOut>,
    /** The 12 calendar months (Jan to Dec) of the current year. */
    val calendarYearMonths: List<MonthInOut> = emptyList(),
    val unconvertedCount: Int,
) {
    val net: Double get() = income - expenses
}

const val RENEWAL_WINDOW_DAYS = 30L
private const val HISTORY_MONTHS = 12L

class AnalyticsViewModel(
    subscriptionRepository: SubscriptionRepository,
    primaryCurrency: Flow<String> = flowOf("USD"),
    exchangeRates: Flow<ExchangeRates?> = flowOf(null),
    paymentRecords: Flow<List<PaymentRecord>> = flowOf(emptyList()),
    budgetPlan: Flow<BudgetPlan> = flowOf(BudgetPlan()),
    spreadLongCharges: Flow<Boolean> = flowOf(false),
    appMode: Flow<AppMode?> = flowOf(AppMode.SUBSCRIPTIONS),
    transactions: Flow<List<Transaction>> = flowOf(emptyList()),
    customCategories: Flow<List<CustomCategory>> = flowOf(emptyList()),
    private val budgets: BudgetRepository? = null,
    private val saveMonthBudget: suspend (YearMonth, Money?) -> Unit = { _, _ -> },
) : ViewModel() {
    // Quarterly and yearly charges spread over the months they cover, when the user wants budgets judged that way.
    private val spread =
        combine(subscriptionRepository.getAllSubscriptions(), spreadLongCharges) { subs, on -> if (on) spreadMap(subs) else emptyMap() }
    private val planAndSpread = combine(budgetPlan, spread) { plan, map -> plan to map }

    private val spendingInputs =
        combine(
            appMode,
            transactions,
            customCategories,
            budgets?.getCategoryBudgets() ?: flowOf(emptyMap()),
        ) { mode, tx, customs, catBudgets ->
            SpendingInputs(mode == AppMode.ALL_SPENDING, tx, customs.associateBy { it.key }, catBudgets)
        }

    private val subscriptionState =
        combine(
            subscriptionRepository.getAllSubscriptions(),
            primaryCurrency,
            exchangeRates,
            paymentRecords,
            planAndSpread,
        ) { all, currency, rates, records, (plan, spreadBy) ->
            val live = all.filter { it.isLive }
            val totals = live.inCurrency(currency, rates)
            val inCurrency = totals.subscriptions
            val today = today()
            val upcoming =
                live
                    .map { UpcomingRenewalItem(it, ChronoUnit.DAYS.between(today, it.nextBillingDate.toLocalDate())) }
                    .filter { it.daysRemaining in 0..RENEWAL_WINDOW_DAYS }
                    .sortedBy { it.subscription.nextBillingDate }

            val history = ExpenseCalculations.monthlyTotals(records, currency, rates, spread = spreadBy)
            val calendarYear = ExpenseCalculations.calendarYearTotals(records, currency, rates, spread = spreadBy)
            val budget = plan.forMonth(YearMonth.from(today))

            AnalyticsUiState(
                currency = currency,
                monthlyBurnRate = ExpenseCalculations.calculateTotalMonthlyBurnRate(inCurrency),
                yearlyProjected = ExpenseCalculations.calculateTotalYearlyProjectedCost(inCurrency),
                trialConversionCost = ExpenseCalculations.calculateTrialConversionCost(inCurrency),
                activeTrialsCount = ExpenseCalculations.calculateActiveTrialsCount(inCurrency),
                currencyNote = CurrencyNote(totals.convertedCount, totals.unconvertedCount, rates?.updatedAtEpochSeconds),
                categoryBreakdown = ExpenseCalculations.calculateCategoryBreakdown(inCurrency),
                upcomingRenewals = upcoming,
                monthlySpend = history.months,
                calendarYearSpend = calendarYear.months,
                unconvertedPayments = history.unconvertedCount,
                budget = budget?.convertTo(currency, rates),
                defaultBudget = plan.default?.convertTo(currency, rates),
                monthBudgets =
                    plan.overrides
                        .mapNotNull { (month, money) ->
                            money.convertTo(currency, rates)?.let { month to it }
                        }.toMap(),
                budgetWaitingForRate = budget?.takeIf { it.convertTo(currency, rates) == null }?.currency,
                hasAnySubscriptions = live.isNotEmpty(),
                isLoading = false,
            )
        }

    val uiState: StateFlow<AnalyticsUiState> =
        combine(subscriptionState, spendingInputs, paymentRecords, exchangeRates, spread) { state, inputs, records, rates, spreadBy ->
            if (!inputs.allSpending) return@combine state
            state.copy(
                spending = spendingAnalytics(inputs, records, state.currency, rates, spreadBy),
                hasAnySubscriptions = state.hasAnySubscriptions || inputs.transactions.isNotEmpty() || records.isNotEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyticsUiState(isLoading = true),
        )

    /** Sets or (with null) removes the monthly budget for one category. */
    fun setCategoryBudget(
        key: String,
        amount: Double?,
    ) {
        // Entered in the primary currency shown on screen, and stored with it.
        val currency = uiState.value.currency
        viewModelScope.launch { budgets?.setCategoryBudget(key, amount?.let { Money(it, currency) }) }
    }

    /** Sets the budget for one [month]; null goes back to the usual budget there. */
    fun setMonthBudget(
        month: YearMonth,
        amount: Double?,
    ) {
        val currency = uiState.value.currency
        viewModelScope.launch { saveMonthBudget(month, amount?.let { Money(it, currency) }) }
    }

    private data class SpendingInputs(
        val allSpending: Boolean,
        val transactions: List<Transaction>,
        val customs: Map<String, CustomCategory>,
        val categoryBudgets: Map<String, Money>,
    )

    private fun spendingAnalytics(
        inputs: SpendingInputs,
        records: List<PaymentRecord>,
        currency: String,
        rates: ExchangeRates?,
        spreadBy: Map<String, Int>,
    ): SpendingAnalytics {
        val current = YearMonth.from(today())
        val summaries =
            (HISTORY_MONTHS - 1 downTo 0).map { back ->
                ExpenseCalculations.monthSummary(inputs.transactions, records, currency, rates, current.minusMonths(back), spreadBy)
            }
        val yearSummaries =
            (1..12).map { monthNum ->
                ExpenseCalculations.monthSummary(
                    inputs.transactions,
                    records,
                    currency,
                    rates,
                    YearMonth.of(current.year, monthNum),
                    spreadBy,
                )
            }
        val now = summaries.last()
        val spent = now.expensesByCategory.associate { it.key to it.total }
        // A budget with no rate to convert it yet is left out rather than compared in the wrong currency.
        val limits = inputs.categoryBudgets.mapNotNull { (key, budget) -> budget.convertTo(currency, rates)?.let { key to it } }.toMap()
        val keys = now.expensesByCategory.map { it.key } + limits.keys.filter { it !in spent }
        return SpendingAnalytics(
            income = now.income,
            expenses = now.expenses,
            slices =
                keys.map { key ->
                    SpendingSlice(
                        resolveCategory(key, TransactionType.EXPENSE, inputs.customs),
                        spent[key] ?: 0.0,
                        limits[key],
                    )
                },
            months = summaries.map { MonthInOut(it.month, it.income, it.expenses) },
            calendarYearMonths = yearSummaries.map { MonthInOut(it.month, it.income, it.expenses) },
            unconvertedCount = now.unconvertedCount,
        )
    }

    class Factory(
        private val repository: SubscriptionRepository,
        private val primaryCurrency: Flow<String> = flowOf("USD"),
        private val exchangeRates: Flow<ExchangeRates?> = flowOf(null),
        private val paymentRecords: Flow<List<PaymentRecord>> = flowOf(emptyList()),
        private val budgetPlan: Flow<BudgetPlan> = flowOf(BudgetPlan()),
        private val spreadLongCharges: Flow<Boolean> = flowOf(false),
        private val appMode: Flow<AppMode?> = flowOf(AppMode.SUBSCRIPTIONS),
        private val transactions: Flow<List<Transaction>> = flowOf(emptyList()),
        private val customCategories: Flow<List<CustomCategory>> = flowOf(emptyList()),
        private val budgets: BudgetRepository? = null,
        private val saveMonthBudget: suspend (YearMonth, Money?) -> Unit = { _, _ -> },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AnalyticsViewModel(
                repository,
                primaryCurrency,
                exchangeRates,
                paymentRecords,
                budgetPlan,
                spreadLongCharges,
                appMode,
                transactions,
                customCategories,
                budgets,
                saveMonthBudget,
            ) as T
    }
}
