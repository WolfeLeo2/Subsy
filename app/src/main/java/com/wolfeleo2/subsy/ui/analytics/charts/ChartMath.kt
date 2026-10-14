package com.wolfeleo2.subsy.ui.analytics.charts

import com.wolfeleo2.subsy.data.util.MonthSpend
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private const val FULL_CIRCLE = 360f

/** A slice of the ring in degrees clockwise from the top, gaps not yet taken out. */
data class Arc(
    val startDegrees: Float,
    val sweepDegrees: Float,
)

/**
 * Slices proportional to [values]. Without a [capacity] they fill the whole circle; with one (a budget) they
 * fill only their part of it, and fill it all once the values reach or pass the capacity.
 */
fun ringSlices(
    values: List<Float>,
    capacity: Float? = null,
): List<Arc> {
    val total = values.sum()
    if (total <= 0f) return emptyList()
    val whole = maxOf(total, capacity ?: total)
    var start = 0f
    return values.map { value ->
        Arc(start, value / whole * FULL_CIRCLE).also { start += it.sweepDegrees }
    }
}

/** Degrees clockwise from straight up (0 until 360) of the point ([dx], [dy]) from the ring's center; y grows downward. */
fun angleFromTop(
    dx: Float,
    dy: Float,
): Float = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat() + FULL_CIRCLE) % FULL_CIRCLE

/** Index of the slice holding [angle], or null when there are none. */
fun sliceAt(
    angle: Float,
    arcs: List<Arc>,
): Int? = arcs.indexOfFirst { angle >= it.startDegrees && angle < it.startDegrees + it.sweepDegrees }.takeIf { it >= 0 }

/** Mean of the non-zero [values], so months before the first logged payment do not drag it down. */
fun averageOfActive(values: List<Double>): Double = values.filter { it > 0.0 }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

/** Mean of the months that had any payment. */
fun averageSpend(months: List<MonthSpend>): Double = averageOfActive(months.map { it.total })

/**
 * Axis values from 0 in a round step (1, 2, 2.5 or 5 times a power of ten) so about [steps] steps reach [max].
 * The last value is at least [max], so the tallest bar never runs off the top.
 */
fun niceTicks(
    max: Double,
    steps: Int = 3,
): List<Double> {
    if (max <= 0.0) return listOf(0.0)
    val raw = max / steps
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = NICE_STEPS.map { it * magnitude }.first { it >= raw }
    return (0..ceil(max / step - TICK_EPSILON).toInt()).map { it * step }
}

private val NICE_STEPS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
private const val TICK_EPSILON = 1e-9

/** Index of the slot under [x] when [width] is split into [count] equal slots, or null outside them. */
fun slotAt(
    x: Float,
    width: Float,
    count: Int,
): Int? = if (count <= 0 || x < 0f || x >= width) null else (x / (width / count)).toInt().coerceAtMost(count - 1)
