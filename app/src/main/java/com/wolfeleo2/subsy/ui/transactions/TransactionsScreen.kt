package com.wolfeleo2.subsy.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.util.MonthSummary
import com.wolfeleo2.subsy.data.util.dailyAllowance
import com.wolfeleo2.subsy.data.util.today
import com.wolfeleo2.subsy.ui.components.CategoryBadge
import com.wolfeleo2.subsy.ui.components.ConnectedButtonGroup
import com.wolfeleo2.subsy.ui.components.EmptyState
import com.wolfeleo2.subsy.ui.components.LoadingState
import com.wolfeleo2.subsy.ui.components.MoneyText
import com.wolfeleo2.subsy.ui.components.OneTimeHint
import com.wolfeleo2.subsy.ui.components.ServiceIconView
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.home.SwipeBackground
import com.wolfeleo2.subsy.ui.navigation.rootToolbarScroll
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular
import com.wolfeleo2.subsy.ui.util.FormattingUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onEdit: (Transaction) -> Unit,
    onOpenSubscription: (Subscription) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /** Charges spotted by detection, shown under the month summary; filled in by the nav graph. */
    spotted: @Composable (Modifier) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.transactions_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val topBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val onDelete: (TransactionRow) -> Unit = { row ->
        viewModel.requestDelete(row.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val amount = FormattingUtils.formatCurrency(row.amount, row.currency)
            val result = snackbar.showSnackbar(deletedMessage.format(amount), undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(row.id)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(topBarScroll.nestedScrollConnection).rootToolbarScroll(),
        topBar = {
            LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.nav_transactions)) }, scrollBehavior = topBarScroll)
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = Spacing.toolbarClearance)) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading -> LoadingState()
                // Spotted charges show here too: a new user's first transactions may well come from them.
                !state.hasAnything ->
                    Column(Modifier.fillMaxSize()) {
                        spotted(Modifier.padding(horizontal = Spacing.gutter))
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = stringResource(R.string.transactions_empty_title),
                            body = stringResource(R.string.transactions_empty_body),
                            action = { Button(onClick = onAdd) { Text(stringResource(R.string.action_add_transaction)) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                else ->
                    TransactionsList(
                        state = state,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                        onFilter = viewModel::setFilter,
                        onOpen = { row ->
                            row.transaction?.let(onEdit) ?: row.subscription?.let(onOpenSubscription)
                        },
                        onDelete = onDelete,
                        spotted = spotted,
                    )
            }
        }
    }
}

@Composable
private fun TransactionsList(
    state: TransactionsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFilter: (TransactionFilter) -> Unit,
    onOpen: (TransactionRow) -> Unit,
    onDelete: (TransactionRow) -> Unit,
    spotted: @Composable (Modifier) -> Unit,
) {
    val monthName = remember(state.month) { state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy")) }
    val isThisMonth = state.month == YearMonth.from(today())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.toolbarClearance),
    ) {
        item(key = "month") {
            MonthStepper(
                if (isThisMonth) stringResource(R.string.transactions_this_month) else monthName,
                state.canGoForward,
                onPrevious,
                onNext,
                Modifier.animateItem(),
            )
        }
        state.summary?.let { summary ->
            item(key = "summary") {
                MonthSummaryCard(summary, state.currency, state.budget, Modifier.padding(vertical = Spacing.s).animateItem())
            }
        }
        item(key = "spotted") { spotted(Modifier.padding(bottom = Spacing.s)) }
        item(key = "filter") {
            val labels =
                mapOf(
                    TransactionFilter.ALL to stringResource(R.string.transactions_filter_all),
                    TransactionFilter.OUT to stringResource(R.string.transactions_filter_expenses),
                    TransactionFilter.IN to stringResource(R.string.kind_income),
                )
            ConnectedButtonGroup(
                options = TransactionFilter.entries,
                selectedOption = state.filter,
                onOptionSelected = onFilter,
                labelProvider = { labels.getValue(it) },
                modifier = Modifier.padding(vertical = Spacing.s).animateItem(),
            )
        }
        val unconverted = state.summary?.unconvertedCount ?: 0
        if (unconverted > 0) {
            item(key = "unconverted") {
                Text(
                    text = pluralStringResource(R.plurals.transactions_unconverted, unconverted, unconverted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs).animateItem(),
                )
            }
        }
        if (state.days.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.transactions_month_empty, monthName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.l).animateItem(),
                )
            }
        }
        state.days.forEachIndexed { i, day -> dayGroup(day, hint = i == 0, onOpen, onDelete) }
    }
}

@Composable
private fun MonthStepper(
    monthName: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = monthName,
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        FilledTonalIconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.transactions_previous_month))
        }
        FilledTonalIconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.transactions_next_month))
        }
    }
}

/**
 * The month at a glance, after Monzo and YNAB: what is left to spend this month, as the big number, with a daily
 * allowance for the rest of the current month, a wavy bar of the budget used, and the income in a pill. Without a
 * budget the income is the base ("Left from income"); with neither it just shows what was spent. Past the limit
 * it reads "Overspent" and turns red.
 */
@Composable
private fun MonthSummaryCard(
    summary: MonthSummary,
    currency: String,
    budget: Double?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val income = summary.income.takeIf { it > 0.0 }
    val limit = budget ?: income
    val left = limit?.let { it - summary.expenses }
    val over = left != null && left < 0.0
    val big = MaterialTheme.typography.displaySmallEmphasized
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (over) scheme.errorContainer else scheme.primaryContainer,
        contentColor = if (over) scheme.onErrorContainer else scheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text =
                    stringResource(
                        when {
                            left == null -> R.string.transactions_spent
                            over -> R.string.transactions_overspent
                            budget != null -> R.string.transactions_left_to_spend
                            else -> R.string.transactions_left_from_income
                        },
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            MoneyText(
                amount = left?.let { abs(it) } ?: summary.expenses,
                currency = currency,
                style = big,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = big.fontSize),
            )
            if (limit != null && left != null) {
                val allowance = dailyAllowance(left, summary.month)
                Text(
                    text =
                        if (allowance != null) {
                            pluralStringResource(
                                R.plurals.transactions_per_day,
                                allowance.days,
                                FormattingUtils.formatCurrency(allowance.perDay, currency, wholeUnits = true),
                                allowance.days,
                            )
                        } else {
                            stringResource(
                                R.string.transactions_spent_of,
                                FormattingUtils.formatCurrency(summary.expenses, currency),
                                FormattingUtils.formatCurrency(limit, currency),
                            )
                        },
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearWavyProgressIndicator(
                    progress = { (summary.expenses / limit).toFloat().coerceIn(0f, 1f) },
                    color = if (over) scheme.error else scheme.primary,
                    trackColor = scheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.m, bottom = Spacing.xs),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.transactions_spent_amount, FormattingUtils.formatCurrency(summary.expenses, currency)),
                        style = MaterialTheme.typography.labelLarge.tabular,
                    )
                    Text(
                        stringResource(
                            if (budget != null) R.string.transactions_budget_amount else R.string.transactions_income_amount,
                            FormattingUtils.formatCurrency(limit, currency),
                        ),
                        style = MaterialTheme.typography.labelLarge.tabular,
                    )
                }
            }
            // With a budget the income is extra information; without one it is already the base shown above.
            if (budget != null && income != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.tertiaryContainer,
                    contentColor = scheme.onTertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.transactions_income_month), style = MaterialTheme.typography.labelLarge)
                        MoneyText(income, currency, style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.dayGroup(
    day: DayGroup,
    hint: Boolean,
    onOpen: (TransactionRow) -> Unit,
    onDelete: (TransactionRow) -> Unit,
) {
    item(key = "day_${day.date}") {
        Text(
            text = dayLabel(day.date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.l, bottom = Spacing.s).animateItem(),
        )
    }
    itemsIndexed(day.rows, key = { _, row -> row.id }) { index, row ->
        Column(Modifier.animateItem()) {
            if (index > 0) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
            val shapes = ListItemDefaults.segmentedShapes(index, day.rows.size)
            if (row.transaction != null) {
                val swipeable = @Composable { SwipeableRow(row, shapes, onOpen = { onOpen(row) }, onDelete = { onDelete(row) }) }
                // The first swipeable row on screen carries the swipe hint.
                if (hint && index == day.rows.indexOfFirst { it.transaction != null }) {
                    OneTimeHint("transactions_swipe", stringResource(R.string.hint_transactions_swipe)) { swipeable() }
                } else {
                    swipeable()
                }
            } else {
                TransactionItem(row, shapes) { onOpen(row) }
            }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = today()
    return when (date) {
        today -> stringResource(R.string.transactions_today)
        today.minusDays(1) -> stringResource(R.string.transactions_yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
}

/** Swipe toward the start deletes, with undo; the same action is offered to screen readers. */
@Composable
private fun SwipeableRow(
    row: TransactionRow,
    shapes: ListItemShapes,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState()
    val deleteLabel = stringResource(R.string.action_delete)
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        backgroundContent = {
            SwipeBackground(
                shape = shapes.shape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                alignEnd = true,
                icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            )
        },
        modifier =
            Modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
            },
    ) {
        TransactionItem(row, shapes, onOpen)
    }
}

@Composable
private fun TransactionItem(
    row: TransactionRow,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val sub = row.subscription
    val note = row.transaction?.note
    val categoryLabel = row.category.label()
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            if (sub != null) {
                ServiceIconView(name = sub.name, colorHex = sub.colorHex, cancellationUrl = sub.cancellationUrl)
            } else {
                CategoryBadge(row.category)
            }
        },
        supportingContent =
            when {
                sub != null -> ({ Text(stringResource(R.string.transactions_subscription_payment)) })
                note != null -> ({ Text(categoryLabel) })
                else -> null
            },
        trailingContent = {
            val income = row.type == TransactionType.INCOME
            MoneyText(
                amount = row.amount,
                currency = row.currency,
                prefix = if (income) "+" else null,
                style = MaterialTheme.typography.bodyLarge,
                color = if (income) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
        content = { Text(sub?.name ?: note ?: categoryLabel) },
    )
}
