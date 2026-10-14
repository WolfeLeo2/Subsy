package com.wolfeleo2.subsy.ui.analytics.charts

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.ui.components.MoneyText
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular
import com.wolfeleo2.subsy.ui.util.FormattingUtils
import com.wolfeleo2.subsy.ui.util.parseColorHex
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STROKE_FRACTION = 0.14f
private const val SELECTED_GROWTH = 0.25f

/** How far an unselected category fades toward the track while another is selected. */
private const val DIM_TOWARD_TRACK = 0.65f
private const val HIT_SLOP = 1.2f
private const val CENTER_WIDTH = 0.46f
private const val FULL_TURN = 359.9f

/** The overflow lap stops short of a full second turn so its head never sits on its own tail. */
private const val MAX_OVERFLOW_DEGREES = 340f

/** Gap between the ring's inner edge and the curved labels, as a share of the stroke. */
private const val LABEL_INSET = 0.3f

@Composable
internal fun Category.color(): Color = parseColorHex(defaultColorHex) ?: MaterialTheme.colorScheme.outline

/** One category on the ring. [key] identifies it for selection. */
data class RingSlice(
    val key: String,
    val label: String,
    val amount: Double,
    val color: Color,
)

/**
 * Spending as a thick ring with one rounded arc per category, labelled along its inside like a fitness ring.
 * Tap an arc to put that category in the center; tap it again, or off the ring, to go back to the total. The
 * category list under it is the non-visual way to do the same.
 *
 * With a [budget] the arcs fill only their share of it and the center shows the percentage used. Past the
 * budget the ring fills and a second, outlined lap shows how far over, with the amount at its head.
 */
@Composable
fun CategoryRing(
    categories: List<RingSlice>,
    currency: String,
    total: Double,
    totalLabel: String,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    budget: Double? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val percent = remember { NumberFormat.getPercentInstance() }
    val sum = categories.sumOf { it.amount }
    val share = { slice: RingSlice -> if (sum > 0) slice.amount / sum else 0.0 }
    val slices = remember(categories, budget) { ringSlices(categories.map { it.amount.toFloat() }, budget?.toFloat()) }
    val over = budget?.let { total - it }?.takeIf { it > 0.0 }
    val overflowDegrees = budget?.let { limit -> over?.let { (it / limit * 360).toFloat().coerceAtMost(MAX_OVERFLOW_DEGREES) } } ?: 0f
    val selectedIndex = categories.indexOfFirst { it.key == selectedKey }
    val selected = categories.getOrNull(selectedIndex)

    // Sweep in once; scrolling the ring away and back must not replay it.
    var played by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            reveal.animateTo(1f, motion.slowSpatialSpec())
            played = true
        }
    }
    val emphasis by animateFloatAsState(if (selected != null) 1f else 0f, motion.defaultEffectsSpec(), label = "ring emphasis")

    val topLabel = selected?.label ?: totalLabel
    val bottomLabel =
        when {
            selected != null -> stringResource(R.string.analytics_share_of_spending, percent.format(share(selected)))
            budget != null ->
                stringResource(
                    R.string.analytics_category_budget,
                    FormattingUtils.formatCurrency(total, currency, wholeUnits = true),
                    FormattingUtils.formatCurrency(budget, currency, wholeUnits = true),
                )
            else -> ""
        }
    val labelPaint = rememberChartPaint(MaterialTheme.typography.labelLarge, scheme.onSurface)
    // The bottom line is the figure, in the accent color like a fitness ring's "188 of 384".
    val bottomPaint =
        rememberChartPaint(
            MaterialTheme.typography.labelLarge,
            if (over != null &&
                selected == null
            ) {
                scheme.error
            } else {
                scheme.primary
            },
        )
    val description =
        stringResource(
            R.string.analytics_bar_description,
            categories.map { stringResource(R.string.analytics_share_item, it.label, percent.format(share(it))) }.joinToString(),
        ) +
            if (budget == null) {
                ""
            } else {
                ". " +
                    stringResource(
                        R.string.analytics_budget_used,
                        percent.format(total / budget),
                        FormattingUtils.formatCurrency(budget, currency),
                    )
            }

    Box(modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices, selectedKey, categories) {
                    detectTapGestures { tap ->
                        val dx = tap.x - size.width / 2f
                        val dy = tap.y - size.height / 2f
                        val geometry = RingGeometry(minOf(size.width, size.height).toFloat())
                        val onRing = abs(hypot(dx, dy) - geometry.radius) <= geometry.stroke * HIT_SLOP
                        val index = if (onRing) sliceAt(angleFromTop(dx, dy), slices) else null
                        onSelect(index?.let { categories[it].key }?.takeUnless { it == selectedKey })
                    }
                },
        ) {
            val geometry = RingGeometry(size.minDimension)
            val revealDegrees = 360f * reveal.value.coerceIn(0f, 1f)
            val track = scheme.surfaceContainerHighest
            drawCircle(track, geometry.radius, style = Stroke(geometry.stroke))
            // Categories butt against each other (their colors already separate them); only the two ends of the
            // whole fill are rounded, so the ring reads as one progress arc.
            val fill = slices.lastOrNull()?.let { it.startDegrees + it.sweepDegrees } ?: 0f
            val revealed = minOf(revealDegrees, fill)
            val colorOf = { index: Int ->
                val base = categories[index].color
                if (selected != null && index != selectedIndex) lerp(base, track, DIM_TOWARD_TRACK * emphasis) else base
            }
            val widthOf = { index: Int ->
                if (index == selectedIndex) geometry.stroke * (1f + SELECTED_GROWTH * emphasis) else geometry.stroke
            }
            slices.forEachIndexed { index, slice ->
                val visible = (revealed - slice.startDegrees).coerceIn(0f, slice.sweepDegrees)
                if (visible > 0f) drawRingArc(colorOf(index), slice.startDegrees, visible, widthOf(index), geometry)
            }
            if (revealed > 0f && fill < FULL_TURN) {
                val head = slices.indexOfLast { it.startDegrees < revealed }
                drawCircle(colorOf(0), widthOf(0) / 2f, geometry.pointAt(0f, center))
                drawCircle(colorOf(head), widthOf(head) / 2f, geometry.pointAt(revealed, center))
            }
            if (overflowDegrees > 0f) drawOverflow(overflowDegrees, geometry, revealDegrees, scheme.error, scheme.surface)
            val labelRadius = geometry.radius - geometry.stroke * (0.5f + LABEL_INSET)
            drawArcText(topLabel, labelPaint, labelRadius, top = true)
            drawArcText(bottomLabel, bottomPaint, labelRadius, top = false)
        }
        Crossfade(
            targetState = selected,
            animationSpec = motion.defaultEffectsSpec(),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(CENTER_WIDTH),
            label = "ring center",
        ) { shown ->
            val bigStyle = MaterialTheme.typography.displaySmallEmphasized
            val fit = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = bigStyle.fontSize)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    shown != null -> MoneyText(shown.amount, currency, style = bigStyle, autoSize = fit)
                    budget != null ->
                        Text(
                            text = percentWithSmallSign(total / budget, bigStyle.fontSize),
                            style = bigStyle.tabular,
                            color = if (over != null) scheme.error else Color.Unspecified,
                            maxLines = 1,
                            autoSize = fit,
                            textAlign = TextAlign.Center,
                        )
                    else -> MoneyText(total, currency, style = bigStyle, autoSize = fit)
                }
            }
        }
        if (over != null && reveal.value >= 1f) OverflowBubble(over, currency, overflowDegrees)
    }
}

/** Stroke and radius for a ring drawn in a square of [side], leaving room for a selected arc to grow. */
private class RingGeometry(
    side: Float,
) {
    val stroke = side * STROKE_FRACTION
    val radius = (side - stroke * (1f + SELECTED_GROWTH)) / 2f

    /** The point on the ring's center line [degrees] clockwise from the top. */
    fun pointAt(
        degrees: Float,
        center: Offset,
    ): Offset {
        val angle = Math.toRadians(degrees.toDouble())
        return Offset(center.x + radius * sin(angle).toFloat(), center.y - radius * cos(angle).toFloat())
    }
}

private fun DrawScope.drawRingArc(
    color: Color,
    startDegrees: Float,
    sweepDegrees: Float,
    width: Float,
    geometry: RingGeometry,
) {
    drawArc(
        color = color,
        startAngle = -90f + startDegrees,
        sweepAngle = sweepDegrees,
        useCenter = false,
        topLeft = Offset(center.x - geometry.radius, center.y - geometry.radius),
        size = Size(geometry.radius * 2f, geometry.radius * 2f),
        style = Stroke(width),
    )
}

/** The second lap past the budget: an error-colored arc from the top, outlined in the surface so it reads as on top. */
private fun DrawScope.drawOverflow(
    degrees: Float,
    geometry: RingGeometry,
    revealDegrees: Float,
    color: Color,
    outline: Color,
) {
    val visible = degrees * (revealDegrees / 360f)
    if (visible <= 0f) return
    val topLeft = Offset(center.x - geometry.radius, center.y - geometry.radius)
    val arcSize = Size(geometry.radius * 2f, geometry.radius * 2f)
    val inset = geometry.stroke * OVERFLOW_STROKE
    drawArc(outline, -90f, visible, false, topLeft, arcSize, style = Stroke(geometry.stroke + inset, cap = StrokeCap.Round))
    drawArc(color, -90f, visible, false, topLeft, arcSize, style = Stroke(geometry.stroke - inset, cap = StrokeCap.Round))
}

private const val OVERFLOW_STROKE = 0.18f

/** "+£12" at the head of the overflow lap. */
@Composable
private fun BoxScope.OverflowBubble(
    amount: Double,
    currency: String,
    degrees: Float,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            Modifier.align(Alignment.TopStart).layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val side = constraints.maxWidth.toFloat()
                val geometry = RingGeometry(side)
                val angle = Math.toRadians(degrees.toDouble())
                val x = side / 2f + geometry.radius * sin(angle).toFloat()
                val y = side / 2f - geometry.radius * cos(angle).toFloat()
                layout(placeable.width, placeable.height) {
                    placeable.place((x - placeable.width / 2f).roundToInt(), (y - placeable.height / 2f).roundToInt())
                }
            },
    ) {
        MoneyText(
            amount = amount,
            currency = currency,
            prefix = "+",
            style = MaterialTheme.typography.labelLargeEmphasized,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
    }
}

/** "49%" with the percent sign at half size, as fitness rings show it. */
private fun percentWithSmallSign(
    fraction: Double,
    size: TextUnit,
): AnnotatedString {
    val text = NumberFormat.getPercentInstance().format(fraction)
    val sign = DecimalFormatSymbols.getInstance().percent
    return buildAnnotatedString {
        text.forEach { char ->
            if (char == sign) withStyle(SpanStyle(fontSize = size * PERCENT_SIGN_SCALE)) { append(char) } else append(char)
        }
    }
}

private const val PERCENT_SIGN_SCALE = 0.5f
