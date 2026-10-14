package com.wolfeleo2.subsy.ui.analytics

import com.wolfeleo2.subsy.data.util.MonthSpend
import com.wolfeleo2.subsy.ui.analytics.charts.angleFromTop
import com.wolfeleo2.subsy.ui.analytics.charts.averageOfActive
import com.wolfeleo2.subsy.ui.analytics.charts.averageSpend
import com.wolfeleo2.subsy.ui.analytics.charts.niceTicks
import com.wolfeleo2.subsy.ui.analytics.charts.ringSlices
import com.wolfeleo2.subsy.ui.analytics.charts.sliceAt
import com.wolfeleo2.subsy.ui.analytics.charts.slotAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ChartMathTest {
    @Test
    fun slicesFillTheCircleEvenWhenSharesDoNotSumToOne() {
        val slices = ringSlices(listOf(0.5f, 0.3f, 0.19f))
        assertEquals(0f, slices.first().startDegrees, 0.001f)
        assertEquals(360f, slices.last().startDegrees + slices.last().sweepDegrees, 0.01f)
        assertEquals(slices[0].sweepDegrees, slices[1].startDegrees, 0.001f)
    }

    @Test
    fun singleSliceIsTheWholeRingAndNoSharesMeansNoSlices() {
        assertEquals(360f, ringSlices(listOf(1f)).single().sweepDegrees, 0.001f)
        assertTrue(ringSlices(emptyList()).isEmpty())
        assertTrue(ringSlices(listOf(0f, 0f)).isEmpty())
    }

    @Test
    fun angleIsMeasuredClockwiseFromTop() {
        assertEquals(0f, angleFromTop(0f, -1f), 0.001f)
        assertEquals(90f, angleFromTop(1f, 0f), 0.001f)
        assertEquals(180f, angleFromTop(0f, 1f), 0.001f)
        assertEquals(270f, angleFromTop(-1f, 0f), 0.001f)
    }

    @Test
    fun sliceAtFindsTheSliceOnEitherSideOfABoundary() {
        val slices = ringSlices(listOf(0.5f, 0.5f))
        assertEquals(0, sliceAt(179.9f, slices))
        assertEquals(1, sliceAt(180f, slices))
        assertEquals(1, sliceAt(359.9f, slices))
        assertNull(sliceAt(10f, emptyList()))
    }

    @Test
    fun averageIgnoresMonthsWithoutPayments() {
        val months = listOf(0.0, 0.0, 10.0, 20.0).mapIndexed { i, total -> MonthSpend(YearMonth.of(2026, i + 1), total) }
        assertEquals(15.0, averageSpend(months), 0.001)
        assertEquals(0.0, averageSpend(months.take(2)), 0.001)
    }

    @Test
    fun underBudgetSlicesFillOnlyTheirPartOfTheRing() {
        val slices = ringSlices(listOf(25f, 25f), capacity = 100f)
        assertEquals(listOf(90f, 90f), slices.map { it.sweepDegrees })
        // The empty half is not a slice, so taps there select nothing.
        assertNull(sliceAt(270f, slices))
    }

    @Test
    fun overBudgetSlicesFillTheWholeRing() {
        val slices = ringSlices(listOf(80f, 40f), capacity = 100f)
        assertEquals(360f, slices.sumOf { it.sweepDegrees.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun niceTicksUseRoundStepsAndReachTheMax() {
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0), niceTicks(13.2))
        assertEquals(listOf(0.0, 20.0, 40.0), niceTicks(40.0))
        assertTrue(niceTicks(987.0).last() >= 987.0)
        assertEquals(listOf(0.0), niceTicks(0.0))
    }

    @Test
    fun slotAtSplitsTheWidthAndIgnoresTheAxis() {
        assertEquals(0, slotAt(0f, 120f, 6))
        assertEquals(5, slotAt(119f, 120f, 6))
        assertNull(slotAt(121f, 120f, 6))
        assertNull(slotAt(-1f, 120f, 6))
    }

    @Test
    fun averageOfActiveSkipsEmptyMonths() {
        assertEquals(15.0, averageOfActive(listOf(0.0, 10.0, 20.0)), 0.001)
        assertEquals(0.0, averageOfActive(listOf(0.0, 0.0)), 0.001)
    }
}
