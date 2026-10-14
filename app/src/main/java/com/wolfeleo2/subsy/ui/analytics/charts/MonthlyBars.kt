package com.wolfeleo2.subsy.ui.analytics.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.util.MonthSpend
import com.wolfeleo2.subsy.ui.components.MoneyText
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.util.FormattingUtils
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val GROUP_FRACTION = 0.72f
private const val BADGE_FRACTION = 0.72f
private const val BADGE_MIN_HEIGHT = 1.3f
private const val CHART_ASPECT = 1.6f
private const val DIMMED_ALPHA = 0.35f
private val AxisWidth = 44.dp
private val GridWidth = 1.dp
private val LineWidth = 1.5.dp
private val MaxBarWidth = 40.dp
private val BadgeGap = 3.dp
private val DashOn = 6.dp
private val DashOff = 4.dp
private val MarkerWidth = 2.5.dp
private val MarkerOverhang = 4.dp

private enum class Badge { NONE, WITHIN, OVER }

/** One pill in a month's group. [marker] is a month's own budget level when it differs from the line. */
private class Bar(
    val value: Double,
    val color: Color,
    val badge: Badge = Badge.NONE,
    val marker: Double? = null,
)

/**
 * Payments per month, oldest on the left, under a headline like a fitness app's step chart: the monthly
 * average and a summary sentence, or the tapped month's total. Each month is judged against its own budget
 * ([budgetFor], which includes one-month overrides): within it the bar is primary with a check badge, over it
 * red with a cross. The dashed line is the usual [defaultBudget], or the average without one; a month with its
 * own budget gets a short marker at that level. Tapping a month offers [onEditMonthBudget] for it.
 */
@Composable
fun MonthlyBars(
    months: List<MonthSpend>,
    budgetFor: (YearMonth) -> Double?,
    defaultBudget: Double?,
    currency: String,
    modifier: Modifier = Modifier,
    /** An extra line under the summary, such as average income. */
    note: String? = null,
    onEditMonthBudget: ((YearMonth) -> Unit)? = null,
) {
    if (months.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    var selected by rememberSaveable(months.size, months.first().month) { mutableStateOf<Int?>(null) }
    val totals = months.map { it.total }
    val limits = months.map { budgetFor(it.month) }
    val average = averageOfActive(totals)
    val line = defaultBudget ?: average
    val active = totals.count { it > 0.0 }
    val judged = months.indices.filter { totals[it] > 0.0 && limits[it] != null }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val pick = selected
        if (pick == null) {
            Headline(average, currency, stringResource(R.string.analytics_avg_per_month))
            Sentence(
                if (judged.isNotEmpty()) {
                    val within = judged.count { totals[it] <= limits[it]!! }
                    pluralStringResource(R.plurals.analytics_within_budget_months, judged.size, within, judged.size)
                } else {
                    pluralStringResource(
                        R.plurals.analytics_total_over_months,
                        months.size,
                        FormattingUtils.formatCurrency(totals.sum(), currency),
                        months.size,
                    )
                },
            )
            note?.let { Sentence(it) }
        } else {
            val total = totals[pick]
            val limit = limits[pick]
            Headline(total, currency, monthName(months[pick].month))
            Sentence(
                when {
                    limit == null -> ""
                    total > limit ->
                        stringResource(
                            R.string.analytics_over_budget_by,
                            FormattingUtils.formatCurrency(total - limit, currency),
                        )
                    else -> stringResource(R.string.analytics_within_budget)
                },
                isError = limit != null && total > limit,
            )
            if (onEditMonthBudget != null) {
                val month = months[pick].month
                FilledTonalButton(onClick = { onEditMonthBudget(month) }, modifier = Modifier.padding(top = Spacing.xs)) {
                    Text(
                        if (limit == null) {
                            stringResource(R.string.analytics_set_month_budget, monthName(month))
                        } else {
                            stringResource(
                                R.string.analytics_edit_month_budget,
                                monthName(month),
                                FormattingUtils.formatCurrency(limit, currency),
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.size(Spacing.s))
        BarChart(
            months = months.map { it.month },
            bars = { index ->
                val value = totals[index]
                val limit = limits[index]
                val badge =
                    when {
                        limit == null || value <= 0.0 -> Badge.NONE
                        value <= limit -> Badge.WITHIN
                        else -> Badge.OVER
                    }
                listOf(
                    Bar(
                        value = value,
                        color = if (badge == Badge.OVER) scheme.error else scheme.primary,
                        badge = badge,
                        marker = limit?.takeIf { it != defaultBudget },
                    ),
                )
            },
            line = line.takeIf { it > 0.0 },
            lineLabel = stringResource(if (defaultBudget != null) R.string.analytics_line_budget else R.string.analytics_line_average),
            lineColor = if (defaultBudget != null) scheme.primary else scheme.onSurfaceVariant,
            currency = currency,
            selected = selected,
            onSelect = { selected = it },
            description =
                stringResource(
                    R.string.analytics_bars_description,
                    months.size,
                    FormattingUtils.formatCurrency(average, currency),
                ),
            spoken = { FormattingUtils.formatCurrency(totals[it], currency) },
        )
    }
}

@Composable
private fun Headline(
    amount: Double,
    currency: String,
    unit: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Bottom) {
        MoneyText(amount, currency, style = MaterialTheme.typography.headlineLargeEmphasized)
        Text(
            unit,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
    }
}

@Composable
private fun Sentence(
    text: String,
    isError: Boolean = false,
) {
    if (text.isEmpty()) return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The shared chart: a group of pills per month on a light grid, a y-axis on the right in compact currency, an
 * optional dashed [line] labelled on the axis, and tap-to-select (others dim) for the caller's headline.
 */
@Composable
private fun BarChart(
    months: List<YearMonth>,
    bars: (Int) -> List<Bar>,
    line: Double?,
    lineLabel: String,
    lineColor: Color,
    currency: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    spoken: (Int) -> String,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val groups = months.indices.map(bars)
    val axisPaint = rememberChartPaint(MaterialTheme.typography.labelSmall, scheme.onSurfaceVariant)
    val linePaint = rememberChartPaint(MaterialTheme.typography.labelSmall, lineColor).apply { isFakeBoldText = true }
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    val check = rememberVectorPainter(Icons.Rounded.Check)
    val cross = rememberVectorPainter(Icons.Rounded.Close)

    // Grow once per range; scrolling away and back must not replay it.
    var grownFor by rememberSaveable { mutableIntStateOf(0) }
    val grow = remember { Animatable(if (grownFor == months.size) 1f else 0f) }
    LaunchedEffect(months.size) {
        if (grownFor != months.size) {
            grow.snapTo(0f)
            grow.animateTo(1f, motion.slowSpatialSpec())
            grownFor = months.size
        }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CHART_ASPECT)
            .semantics { contentDescription = description }
            .pointerInput(months.size, selected) {
                detectTapGestures { tap ->
                    val index = slotAt(tap.x, size.width - AxisWidth.toPx(), months.size)
                    onSelect(index?.takeUnless { it == selected })
                }
            },
    ) {
        val axis = AxisWidth.toPx()
        val plotWidth = size.width - axis
        val slot = plotWidth / months.size
        // Bars stay a fixed, chunky width however few months are shown; a single month must not fill the chart.
        val barWidth = minOf(slot * GROUP_FRACTION / groups.maxOf { it.size }, MaxBarWidth.toPx())
        val badgeSide = barWidth * BADGE_FRACTION
        // Room above the top tick for a badge sitting on a full-height bar.
        val plotTop = maxOf(axisPaint.textSize, badgeSide + BadgeGap.toPx())
        val plotHeight = size.height - plotTop
        val levels = groups.flatten().flatMap { listOfNotNull(it.value, it.marker) } + listOfNotNull(line)
        val ticks = niceTicks(levels.maxOrNull() ?: 0.0)
        val top = ticks.last().coerceAtLeast(0.01)
        val yOf = { value: Double -> plotTop + plotHeight * (1f - (value / top).toFloat()) }

        ticks.forEach { tick ->
            val y = yOf(tick)
            drawLine(scheme.outlineVariant, Offset(0f, y), Offset(plotWidth, y), strokeWidth = GridWidth.toPx())
            drawLabel(FormattingUtils.formatCompactCurrency(tick, currency), axisPaint, plotWidth + axis / 2f, y + axisPaint.textSize / 3f)
        }

        groups.forEachIndexed { index, group ->
            val left = index * slot + (slot - barWidth * group.size) / 2f
            val dim = selected != null && selected != index
            val alpha = if (dim) DIMMED_ALPHA else 1f
            // Months with nothing spent draw nothing: a stub would read as a tiny payment.
            group.forEachIndexed { which, bar ->
                if (bar.value <= 0.0) return@forEachIndexed
                val full = plotHeight * (bar.value / top).toFloat()
                val height = minOf(plotHeight, maxOf(barWidth, full * grow.value.coerceIn(0f, 1f)))
                val x = left + which * barWidth
                val y = plotTop + plotHeight - height
                drawRoundRect(bar.color.copy(alpha = alpha), Offset(x, y), Size(barWidth, height), CornerRadius(barWidth / 2f))
                bar.marker?.let { level ->
                    val my = yOf(level)
                    val overhang = MarkerOverhang.toPx()
                    drawLine(
                        lineColor.copy(alpha = alpha),
                        Offset(x - overhang, my),
                        Offset(x + barWidth + overhang, my),
                        MarkerWidth.toPx(),
                        StrokeCap.Round,
                    )
                }
                if (bar.badge != Badge.NONE) {
                    val within = bar.badge == Badge.WITHIN
                    val inset = (barWidth - badgeSide) / 2f
                    // Inside the top of a tall bar, as in the step chart; on top of a short one so it is never lost.
                    val badgeY = if (height >= barWidth * BADGE_MIN_HEIGHT) y + inset else y - badgeSide - BadgeGap.toPx()
                    translate(x + inset, badgeY) {
                        val side = Size(badgeSide, badgeSide)
                        drawOutline(
                            cookie.createOutline(side, layoutDirection, this),
                            (if (within) scheme.primaryContainer else scheme.errorContainer).copy(alpha = alpha),
                        )
                        val icon = badgeSide * BADGE_FRACTION
                        translate((badgeSide - icon) / 2f, (badgeSide - icon) / 2f) {
                            with(if (within) check else cross) {
                                draw(
                                    Size(icon, icon),
                                    alpha = alpha,
                                    colorFilter = ColorFilter.tint(if (within) scheme.onPrimaryContainer else scheme.onErrorContainer),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (line != null && line > 0.0) {
            val y = yOf(line)
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(plotWidth, y),
                strokeWidth = LineWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
            )
            // The line's label replaces the axis value under it, on a surface chip so the two never overlap.
            val chipHeight = linePaint.textSize * 1.5f
            drawRoundRect(
                color = scheme.surface,
                topLeft = Offset(plotWidth + 2f, y - chipHeight / 2f),
                size = Size(axis - 2f, chipHeight),
                cornerRadius = CornerRadius(chipHeight / 2f),
            )
            drawLabel(lineLabel, linePaint, plotWidth + axis / 2f, y + linePaint.textSize / 3f)
        }
    }
    MonthLabels(months, selected, spoken)
}

private fun monthName(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))

/** Narrow month initials under the bars (not under the axis); each reads its full month and [spoken] to screen readers. */
@Composable
private fun MonthLabels(
    months: List<YearMonth>,
    selected: Int?,
    spoken: (Int) -> String,
) {
    val locale = Locale.getDefault()
    val currentMonth = remember { YearMonth.now() }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(end = AxisWidth)) {
        months.forEachIndexed { index, month ->
            val emphasized = month == currentMonth || index == selected
            val words = "${month.month.getDisplayName(TextStyle.FULL, locale)} ${spoken(index)}"
            Text(
                text = month.month.getDisplayName(TextStyle.NARROW, locale),
                style = if (emphasized) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                color = if (emphasized) colors.onSurface else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { contentDescription = words },
            )
        }
    }
}
