package com.wolfeleo2.subsy.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.util.CategorySpend
import com.wolfeleo2.subsy.data.util.MonthSpend
import com.wolfeleo2.subsy.ui.analytics.charts.CategoryRing
import com.wolfeleo2.subsy.ui.analytics.charts.MonthlyBars
import com.wolfeleo2.subsy.ui.analytics.charts.RingSlice
import com.wolfeleo2.subsy.ui.analytics.charts.color
import com.wolfeleo2.subsy.ui.components.BudgetDialog
import com.wolfeleo2.subsy.ui.components.ConnectedButtonGroup
import com.wolfeleo2.subsy.ui.components.CurrencyNote
import com.wolfeleo2.subsy.ui.components.CurrencyNoteLines
import com.wolfeleo2.subsy.ui.components.EmptyState
import com.wolfeleo2.subsy.ui.components.LoadingState
import com.wolfeleo2.subsy.ui.components.MoneyText
import com.wolfeleo2.subsy.ui.components.SectionHeader
import com.wolfeleo2.subsy.ui.components.ServiceIconView
import com.wolfeleo2.subsy.ui.components.StatTone
import com.wolfeleo2.subsy.ui.components.isUrgent
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.components.nextEventLabel
import com.wolfeleo2.subsy.ui.navigation.ORIGIN_ANALYTICS
import com.wolfeleo2.subsy.ui.navigation.detailContainer
import com.wolfeleo2.subsy.ui.navigation.detailIcon
import com.wolfeleo2.subsy.ui.navigation.detailTransitionKey
import com.wolfeleo2.subsy.ui.navigation.rootToolbarScroll
import com.wolfeleo2.subsy.ui.theme.AppTheme
import com.wolfeleo2.subsy.ui.theme.Spacing
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onNavigateToDetail: (Subscription) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnalyticsScreenContent(
        uiState = uiState,
        onNavigateToDetail = onNavigateToDetail,
        onAdd = onAdd,
        onSetCategoryBudget = viewModel::setCategoryBudget,
        onSetMonthBudget = viewModel::setMonthBudget,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreenContent(
    uiState: AnalyticsUiState,
    onNavigateToDetail: (Subscription) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onSetCategoryBudget: (key: String, amount: Double?) -> Unit = { _, _ -> },
    onSetMonthBudget: (month: YearMonth, amount: Double?) -> Unit = { _, _ -> },
) {
    val topBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(topBarScroll.nestedScrollConnection)
                .rootToolbarScroll(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                scrollBehavior = topBarScroll,
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading -> LoadingState()
                !uiState.hasAnySubscriptions ->
                    EmptyState(
                        icon = Icons.Rounded.DonutLarge,
                        title = stringResource(R.string.analytics_empty_title),
                        body = stringResource(R.string.analytics_empty_body),
                        action = { Button(onClick = onAdd) { Text(stringResource(R.string.action_add_subscription)) } },
                    )
                else -> AnalyticsContent(uiState, onNavigateToDetail, onSetCategoryBudget, onSetMonthBudget)
            }
        }
    }
}

private enum class RenewalBucket(
    val labelRes: Int,
    val days: LongRange,
) {
    THIS_WEEK(R.string.analytics_bucket_this_week, 0L..6L),
    NEXT_WEEK(R.string.analytics_bucket_next_week, 7L..13L),
    LATER(R.string.analytics_bucket_later, 14L..RENEWAL_WINDOW_DAYS),
}

@Composable
private fun AnalyticsContent(
    uiState: AnalyticsUiState,
    onNavigateToDetail: (Subscription) -> Unit,
    onSetCategoryBudget: (key: String, amount: Double?) -> Unit,
    onSetMonthBudget: (month: YearMonth, amount: Double?) -> Unit,
) {
    val percent = remember { NumberFormat.getPercentInstance() }
    val categories = uiState.categoryBreakdown
    val renewals = uiState.upcomingRenewals
    val buckets = remember(renewals) { RenewalBucket.entries.associateWith { b -> renewals.filter { it.daysRemaining in b.days } } }
    val primaryRenewals = renewals.filter { it.subscription.currency == uiState.currency }
    val scheme = MaterialTheme.colorScheme
    val segmentColors = ListItemDefaults.segmentedColors(containerColor = scheme.surfaceContainer)
    val selectedColors = ListItemDefaults.segmentedColors(containerColor = scheme.secondaryContainer)
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var budgetFor by rememberSaveable { mutableStateOf<String?>(null) }
    // The month whose own budget is being edited, as "2026-12" (saveable across rotation).
    var monthBudgetFor by rememberSaveable { mutableStateOf<String?>(null) }
    val spending = uiState.spending
    var rangeMonths by rememberSaveable { mutableIntStateOf(DEFAULT_RANGE_MONTHS) }
    val shownMonths =
        remember(uiState.monthlySpend, uiState.calendarYearSpend, rangeMonths) {
            if (rangeMonths == 12) {
                uiState.calendarYearSpend
            } else {
                uiState.monthlySpend.takeLast(rangeMonths)
            }
        }
    val nextCharge = renewals.firstOrNull()?.subscription

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.toolbarClearance),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        if (spending != null) {
            spendingSections(
                spending = spending,
                currency = uiState.currency,
                budget = uiState.budget,
                budgetWaitingForRate = uiState.budgetWaitingForRate,
                selectedKey = selectedKey,
                onSelect = { selectedKey = it },
                rangeMonths = rangeMonths,
                onRange = { rangeMonths = it },
                onEditBudget = { budgetFor = it },
                budgetFor = uiState::budgetFor,
                defaultBudget = uiState.defaultBudget,
                onEditMonthBudget = { monthBudgetFor = it.toString() },
            )
        } else {
            item(key = "hero") {
                val trials = uiState.activeTrialsCount
                val stats =
                    buildList {
                        add(
                            HeroStat(
                                Icons.Rounded.CalendarMonth,
                                stringResource(R.string.analytics_tile_yearly),
                                uiState.yearlyProjected,
                                uiState.currency,
                                tone = StatTone.PRIMARY,
                            ),
                        )
                        if (trials > 0) {
                            add(
                                HeroStat(
                                    icon = Icons.Rounded.HourglassTop,
                                    label = stringResource(R.string.analytics_tile_trials),
                                    amount = uiState.trialConversionCost,
                                    currency = uiState.currency,
                                    tone = StatTone.TRIAL,
                                    supporting = pluralStringResource(R.plurals.analytics_tile_trials_count, trials, trials),
                                    suffix = stringResource(R.string.suffix_month),
                                ),
                            )
                        }
                        if (nextCharge != null) {
                            add(
                                HeroStat(
                                    icon = Icons.Rounded.Event,
                                    label = nextCharge.name,
                                    amount = nextCharge.amount,
                                    currency = nextCharge.currency,
                                    tone = if (nextCharge.isUrgent) StatTone.URGENT else StatTone.SECONDARY,
                                    supporting = nextCharge.nextEventLabel(),
                                ),
                            )
                        }
                    }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.l).animateItem(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    RingHero(stats) { ringModifier ->
                        CategoryRing(
                            categories =
                                categories.map {
                                    RingSlice(
                                        it.category.name,
                                        it.category.label(),
                                        it.monthlyTotal,
                                        it.category.color(),
                                    )
                                },
                            currency = uiState.currency,
                            total = uiState.monthlyBurnRate,
                            totalLabel = stringResource(R.string.hero_label),
                            selectedKey = selectedKey,
                            onSelect = { selectedKey = it },
                            budget = uiState.budget,
                            modifier = ringModifier,
                        )
                    }
                    CurrencyNoteLines(uiState.currencyNote)
                    uiState.budgetWaitingForRate?.let { BudgetRateNote(it) }
                }
            }

            item(key = "months_header") { SectionHeader(stringResource(R.string.analytics_by_month), Modifier.animateItem()) }
            item(key = "months_range") {
                val labels =
                    mapOf(
                        1 to stringResource(R.string.analytics_range_1m),
                        3 to stringResource(R.string.analytics_range_3m),
                        6 to stringResource(R.string.analytics_range_6m),
                        12 to stringResource(R.string.analytics_range_12m),
                    )
                ConnectedButtonGroup(
                    options = RANGE_OPTIONS,
                    selectedOption = rangeMonths,
                    onOptionSelected = { rangeMonths = it },
                    labelProvider = { labels.getValue(it) },
                    modifier = Modifier.padding(bottom = Spacing.m).animateItem(),
                )
            }
            item(key = "months_chart") {
                if (shownMonths.any { it.total > 0.0 }) {
                    MonthlyBars(
                        months = shownMonths,
                        budgetFor = uiState::budgetFor,
                        defaultBudget = uiState.defaultBudget,
                        currency = uiState.currency,
                        onEditMonthBudget = { monthBudgetFor = it.toString() },
                        modifier = Modifier.animateItem(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.analytics_no_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs).animateItem(),
                    )
                }
            }
            if (uiState.unconvertedPayments > 0) {
                item(key = "months_note") {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.analytics_payments_unconverted,
                                uiState.unconvertedPayments,
                                uiState.unconvertedPayments,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s).animateItem(),
                    )
                }
            }

            if (categories.isNotEmpty()) {
                item(key = "categories_header") { SectionHeader(stringResource(R.string.analytics_where_it_goes), Modifier.animateItem()) }
                itemsIndexed(categories, key = { _, c -> "category_${c.category.name}" }) { index, item ->
                    SegmentedListItem(
                        onClick = { selectedKey = item.category.name.takeUnless { it == selectedKey } },
                        shapes = ListItemDefaults.segmentedShapes(index, categories.size),
                        colors = if (item.category.name == selectedKey) selectedColors else segmentColors,
                        modifier = Modifier.animateItem(),
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(item.category.color()),
                            )
                        },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.analytics_subscription_count,
                                    item.subscriptionCount,
                                    item.subscriptionCount,
                                ),
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(
                                    amount = item.monthlyTotal,
                                    currency = uiState.currency,
                                    style = MaterialTheme.typography.bodyLarge,
                                    suffix = stringResource(R.string.suffix_month),
                                )
                                Text(
                                    text = percent.format(item.share.toDouble()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        content = { Text(item.category.label()) },
                    )
                }
            }
        }

        item(key = "renewals_header") {
            SectionHeader(
                text = stringResource(R.string.analytics_next_30_days),
                modifier = Modifier.animateItem(),
                trailing =
                    if (primaryRenewals.isEmpty()) {
                        null
                    } else {
                        {
                            MoneyText(
                                amount = primaryRenewals.sumOf { it.subscription.amount },
                                currency = uiState.currency,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    },
            )
        }
        if (renewals.isEmpty()) {
            item(key = "renewals_empty") {
                Text(
                    text = stringResource(R.string.analytics_no_renewals),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs).animateItem(),
                )
            }
        } else {
            buckets.forEach { (bucket, list) ->
                if (list.isNotEmpty()) renewalBucket(bucket, list, segmentColors, onNavigateToDetail)
            }
        }
    }

    monthBudgetFor?.let { YearMonth.parse(it) }?.let { month ->
        BudgetDialog(
            title = stringResource(R.string.analytics_month_budget_title, month.format(DateTimeFormatter.ofPattern("LLLL yyyy"))),
            // Starts from the budget in effect; only a month's own budget can be removed (back to the usual one).
            current = uiState.budgetFor(month),
            removable = month in uiState.monthBudgets,
            currency = uiState.currency,
            onSave = {
                onSetMonthBudget(month, it)
                monthBudgetFor = null
            },
            onDismiss = { monthBudgetFor = null },
        )
    }
    val budgetKey = budgetFor
    val budgetSlice = spending?.slices?.firstOrNull { it.category.key == budgetKey }
    if (budgetKey != null) {
        BudgetDialog(
            title = stringResource(R.string.analytics_budget_dialog_title, budgetSlice?.category?.label() ?: ""),
            current = budgetSlice?.budget,
            currency = uiState.currency,
            onSave = {
                onSetCategoryBudget(budgetKey, it)
                budgetFor = null
            },
            onDismiss = { budgetFor = null },
        )
    }
}

internal const val RING_WIDTH = 0.85f
internal const val DEFAULT_RANGE_MONTHS = 6
internal val RANGE_OPTIONS = listOf(1, 3, 6, 12)

private fun LazyListScope.renewalBucket(
    bucket: RenewalBucket,
    list: List<UpcomingRenewalItem>,
    colors: ListItemColors,
    onNavigateToDetail: (Subscription) -> Unit,
) {
    item(key = "bucket_${bucket.name}") {
        Text(
            text = stringResource(bucket.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.s, bottom = Spacing.xs).animateItem(),
        )
    }
    itemsIndexed(list, key = { _, r -> r.subscription.id }) { index, item ->
        val sub = item.subscription
        val transitionKey = detailTransitionKey(ORIGIN_ANALYTICS, sub.id)
        val shapes = ListItemDefaults.segmentedShapes(index, list.size)
        SegmentedListItem(
            onClick = { onNavigateToDetail(sub) },
            shapes = shapes,
            colors = colors,
            modifier = Modifier.animateItem().detailContainer(transitionKey, shapes.shape),
            leadingContent = {
                ServiceIconView(
                    name = sub.name,
                    colorHex = sub.colorHex,
                    cancellationUrl = sub.cancellationUrl,
                    modifier = Modifier.detailIcon(transitionKey),
                )
            },
            supportingContent = {
                Text(
                    text = sub.nextEventLabel(),
                    color = if (sub.status == SubscriptionStatus.TRIAL) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                )
            },
            trailingContent = { MoneyText(sub.amount, sub.currency, style = MaterialTheme.typography.bodyLarge) },
            content = { Text(sub.name) },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AnalyticsScreenPreview() {
    val sampleSubscription1 =
        Subscription(
            id = "1",
            name = "Netflix",
            category = Category.ENTERTAINMENT,
            amount = 15.99,
            currency = "USD",
            billingCycle = BillingCycle.MONTHLY,
            startDate = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
            nextBillingDate = System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000,
            isFreeTrial = false,
            status = SubscriptionStatus.ACTIVE,
            colorHex = "#E50914",
        )
    val sampleSubscription2 =
        Subscription(
            id = "2",
            name = "Spotify",
            category = Category.ENTERTAINMENT,
            amount = 10.99,
            currency = "USD",
            billingCycle = BillingCycle.MONTHLY,
            startDate = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000,
            nextBillingDate = System.currentTimeMillis() + 8L * 24 * 60 * 60 * 1000,
            isFreeTrial = false,
            status = SubscriptionStatus.ACTIVE,
            colorHex = "#1DB954",
        )
    val sampleSubscription3 =
        Subscription(
            id = "3",
            name = "GitHub Pro",
            category = Category.WORK_SAAS,
            amount = 4.00,
            currency = "USD",
            billingCycle = BillingCycle.MONTHLY,
            startDate = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000,
            nextBillingDate = System.currentTimeMillis() + 20L * 24 * 60 * 60 * 1000,
            isFreeTrial = false,
            status = SubscriptionStatus.ACTIVE,
            colorHex = "#2DBA4E",
        )

    val sampleUiState =
        AnalyticsUiState(
            currency = "USD",
            monthlyBurnRate = 30.98,
            yearlyProjected = 371.76,
            trialConversionCost = 0.0,
            activeTrialsCount = 0,
            currencyNote = CurrencyNote(),
            categoryBreakdown =
                listOf(
                    CategorySpend(
                        category = Category.ENTERTAINMENT,
                        monthlyTotal = 26.98,
                        share = 0.87f,
                        subscriptionCount = 2,
                    ),
                    CategorySpend(
                        category = Category.WORK_SAAS,
                        monthlyTotal = 4.00,
                        share = 0.13f,
                        subscriptionCount = 1,
                    ),
                ),
            upcomingRenewals =
                listOf(
                    UpcomingRenewalItem(subscription = sampleSubscription1, daysRemaining = 2),
                    UpcomingRenewalItem(subscription = sampleSubscription2, daysRemaining = 8),
                    UpcomingRenewalItem(subscription = sampleSubscription3, daysRemaining = 20),
                ),
            monthlySpend =
                listOf(0.0, 0.0, 24.0, 31.0, 28.0, 36.0, 30.98, 30.98, 41.0, 30.98, 30.98, 12.0).mapIndexed { i, total ->
                    MonthSpend(YearMonth.now().minusMonths(11L - i), total)
                },
            hasAnySubscriptions = true,
            isLoading = false,
        )

    AppTheme {
        AnalyticsScreenContent(
            uiState = sampleUiState,
            onNavigateToDetail = {},
            onAdd = {},
        )
    }
}
