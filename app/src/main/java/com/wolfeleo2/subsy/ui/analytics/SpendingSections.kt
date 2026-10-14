package com.wolfeleo2.subsy.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.CategoryRef
import com.wolfeleo2.subsy.data.util.MonthSpend
import com.wolfeleo2.subsy.ui.analytics.charts.CategoryRing
import com.wolfeleo2.subsy.ui.analytics.charts.MonthlyBars
import com.wolfeleo2.subsy.ui.analytics.charts.RingSlice
import com.wolfeleo2.subsy.ui.analytics.charts.averageOfActive
import com.wolfeleo2.subsy.ui.components.CategoryBadge
import com.wolfeleo2.subsy.ui.components.ConnectedButtonGroup
import com.wolfeleo2.subsy.ui.components.MoneyText
import com.wolfeleo2.subsy.ui.components.SectionHeader
import com.wolfeleo2.subsy.ui.components.StatTone
import com.wolfeleo2.subsy.ui.components.color
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.util.FormattingUtils
import java.time.YearMonth
import kotlin.math.abs

/** Spending in All spending mode: this month's ring, in/out/net, monthly history and categories with budgets. */
internal fun LazyListScope.spendingSections(
    spending: SpendingAnalytics,
    currency: String,
    budget: Double?,
    budgetWaitingForRate: String?,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    rangeMonths: Int,
    onRange: (Int) -> Unit,
    onEditBudget: (key: String) -> Unit,
    budgetFor: (YearMonth) -> Double?,
    defaultBudget: Double?,
    onEditMonthBudget: (YearMonth) -> Unit,
) {
    item(key = "spending_hero") {
        // Spent fills against the budget, or the income without one; left over fills as the share of income kept.
        val limit = budget ?: spending.income.takeIf { it > 0.0 }
        val income = spending.income.takeIf { it > 0.0 }
        val stats =
            listOf(
                HeroStat(
                    Icons.AutoMirrored.Rounded.TrendingUp,
                    stringResource(R.string.transactions_in),
                    spending.income,
                    currency,
                    tone = StatTone.PRIMARY,
                ),
                HeroStat(
                    Icons.AutoMirrored.Rounded.TrendingDown,
                    stringResource(R.string.transactions_out),
                    spending.expenses,
                    currency,
                    tone = if (budget != null && spending.expenses > budget) StatTone.URGENT else StatTone.TRIAL,
                    progress = limit?.let { (spending.expenses / it).toFloat() },
                ),
                HeroStat(
                    Icons.Rounded.AccountBalanceWallet,
                    stringResource(if (spending.net < 0) R.string.transactions_overspent else R.string.transactions_net),
                    abs(spending.net),
                    currency,
                    tone = if (spending.net < 0) StatTone.URGENT else StatTone.SECONDARY,
                    progress = if (spending.net < 0) 1f else income?.let { (spending.net / it).toFloat() },
                ),
            )
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.l).animateItem(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            RingHero(stats) { ringModifier ->
                CategoryRing(
                    // Budgeted categories with nothing spent yet stay off the ring; their rows still show.
                    categories =
                        spending.slices
                            .filter { it.total > 0.0 }
                            .map { RingSlice(it.category.key, it.category.label(), it.total, it.category.color()) },
                    currency = currency,
                    total = spending.expenses,
                    totalLabel = stringResource(R.string.analytics_spent_this_month),
                    selectedKey = selectedKey,
                    onSelect = onSelect,
                    budget = budget,
                    modifier = ringModifier,
                )
            }
            budgetWaitingForRate?.let { BudgetRateNote(it) }
            spending.slices.firstOrNull { it.category.key == selectedKey }?.let { slice ->
                val name = slice.category.label()
                FilledTonalButton(onClick = { onEditBudget(slice.category.key) }) {
                    Text(
                        if (slice.budget == null) {
                            stringResource(R.string.analytics_set_budget, name)
                        } else {
                            stringResource(R.string.analytics_edit_budget, name, FormattingUtils.formatCurrency(slice.budget, currency))
                        },
                    )
                }
            }
        }
    }

    item(key = "in_out_header") { SectionHeader(stringResource(R.string.analytics_spending_by_month), Modifier.animateItem()) }
    item(key = "in_out_range") {
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
            onOptionSelected = onRange,
            labelProvider = { labels.getValue(it) },
            modifier = Modifier.padding(bottom = Spacing.m).animateItem(),
        )
    }
    item(key = "in_out_chart") {
        val shown =
            if (rangeMonths == 12) {
                spending.calendarYearMonths
            } else {
                spending.months.takeLast(rangeMonths)
            }
        if (shown.any { it.income > 0.0 || it.expenses > 0.0 }) {
            val income = averageOfActive(shown.map { it.income })
            MonthlyBars(
                months = shown.map { MonthSpend(it.month, it.expenses) },
                budgetFor = budgetFor,
                defaultBudget = defaultBudget,
                currency = currency,
                onEditMonthBudget = onEditMonthBudget,
                note =
                    if (income >
                        0.0
                    ) {
                        stringResource(R.string.analytics_income_avg, FormattingUtils.formatCurrency(income, currency))
                    } else {
                        null
                    },
                modifier = Modifier.animateItem(),
            )
        } else {
            Text(
                text = stringResource(R.string.analytics_no_history),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs).animateItem(),
            )
        }
    }
    if (spending.unconvertedCount > 0) {
        item(key = "spending_unconverted") {
            Text(
                text = pluralStringResource(R.plurals.transactions_unconverted, spending.unconvertedCount, spending.unconvertedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s).animateItem(),
            )
        }
    }

    item(key = "spending_categories_header") {
        SectionHeader(stringResource(R.string.analytics_where_it_goes), Modifier.animateItem())
    }
    if (spending.slices.isEmpty()) {
        item(key = "spending_empty") {
            Text(
                text = stringResource(R.string.analytics_no_spending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs).animateItem(),
            )
        }
    }
    itemsIndexed(spending.slices, key = { _, slice -> "spending_${slice.category.key}" }) { index, slice ->
        val scheme = MaterialTheme.colorScheme
        val selected = slice.category.key == selectedKey
        val over = slice.budget != null && slice.total > slice.budget
        SegmentedListItem(
            onClick = { onSelect(slice.category.key.takeUnless { selected }) },
            shapes = ListItemDefaults.segmentedShapes(index, spending.slices.size),
            colors =
                ListItemDefaults.segmentedColors(
                    containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
                ),
            modifier = Modifier.animateItem(),
            leadingContent = { BudgetBadge(slice.category, slice.budget?.let { (slice.total / it).toFloat() }) },
            supportingContent =
                slice.budget?.let { limit ->
                    {
                        Text(
                            text =
                                if (over) {
                                    stringResource(
                                        R.string.analytics_category_over,
                                        FormattingUtils.formatCurrency(slice.total - limit, currency),
                                        FormattingUtils.formatCurrency(limit, currency),
                                    )
                                } else {
                                    stringResource(
                                        R.string.analytics_category_budget,
                                        FormattingUtils.formatCurrency(slice.total, currency),
                                        FormattingUtils.formatCurrency(limit, currency),
                                    )
                                },
                            color = if (over) scheme.error else scheme.onSurfaceVariant,
                        )
                    }
                },
            trailingContent = { MoneyText(slice.total, currency, style = MaterialTheme.typography.bodyLarge) },
            content = { Text(slice.category.label()) },
        )
    }
}

/** The category badge, ringed by how much of its budget is used ([fraction]); red once over. */
@Composable
private fun BudgetBadge(
    category: CategoryRef,
    fraction: Float?,
) {
    if (fraction == null) {
        CategoryBadge(category)
        return
    }
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(scheme.surfaceContainerHighest, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(
                color = if (fraction > 1f) scheme.error else scheme.primary,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        CategoryBadge(category, size = 36.dp)
    }
}

/** Shown when a budget is set in another currency and no exchange rate has loaded to convert it. */
@Composable
internal fun BudgetRateNote(currency: String) {
    Text(
        text = stringResource(R.string.analytics_budget_waiting, currency),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
