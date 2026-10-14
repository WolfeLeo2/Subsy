package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.BillingCycleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class BillingCycleTest {
    private val utc = ZoneId.of("UTC")

    private fun millis(date: String): Long =
        LocalDate
            .parse(date)
            .atStartOfDay(utc)
            .toInstant()
            .toEpochMilli()

    private fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(utc).toLocalDate()

    @Test
    fun serializationAndDeserialization() {
        assertEquals("MONTHLY", BillingCycle.MONTHLY.serializedName)
        assertEquals(BillingCycle.MONTHLY, BillingCycle.fromSerialized("MONTHLY"))

        assertEquals("YEARLY", BillingCycle.YEARLY.serializedName)
        assertEquals(BillingCycle.YEARLY, BillingCycle.fromSerialized("YEARLY"))

        val custom14 = BillingCycle.custom(14)
        assertEquals("CUSTOM_14", custom14.serializedName)
        val parsedCustom = BillingCycle.fromSerialized("CUSTOM_14")
        assertEquals(BillingCycleType.CUSTOM, parsedCustom.type)
        assertEquals(14, parsedCustom.customDays)
    }

    @Test
    fun normalizedMonthlyAmountCalculations() {
        // Monthly $15 stays $15
        assertEquals(15.0, BillingCycle.MONTHLY.normalizedMonthlyAmount(15.0), 0.001)

        // Yearly $120 converts to $10/month
        assertEquals(10.0, BillingCycle.YEARLY.normalizedMonthlyAmount(120.0), 0.001)

        // Quarterly $30 converts to $10/month
        assertEquals(10.0, BillingCycle.QUARTERLY.normalizedMonthlyAmount(30.0), 0.001)

        // Bi-annually $60 converts to $10/month
        assertEquals(10.0, BillingCycle.BI_ANNUALLY.normalizedMonthlyAmount(60.0), 0.001)

        // Weekly $10 converts to $10 * (365.25 / 12) / 7 = $43.482/month
        assertEquals(43.482, BillingCycle.WEEKLY.normalizedMonthlyAmount(10.0), 0.001)
    }

    @Test
    fun nextBillingDateCalculation() {
        val zone = ZoneId.of("UTC")
        val baseDate = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

        // 1 month advancement
        val nextMonth = BillingCycle.MONTHLY.calculateNextBillingDate(baseDate, zone)
        val nextMonthDateTime = Instant.ofEpochMilli(nextMonth).atZone(zone)
        assertEquals(2026, nextMonthDateTime.year)
        assertEquals(2, nextMonthDateTime.monthValue)
        assertEquals(15, nextMonthDateTime.dayOfMonth)

        // 1 year advancement
        val nextYear = BillingCycle.YEARLY.calculateNextBillingDate(baseDate, zone)
        val nextYearDateTime = Instant.ofEpochMilli(nextYear).atZone(zone)
        assertEquals(2027, nextYearDateTime.year)
        assertEquals(1, nextYearDateTime.monthValue)
        assertEquals(15, nextYearDateTime.dayOfMonth)

        // 1 week advancement
        val nextWeek = BillingCycle.WEEKLY.calculateNextBillingDate(baseDate, zone)
        val nextWeekDateTime = Instant.ofEpochMilli(nextWeek).atZone(zone)
        assertEquals(22, nextWeekDateTime.dayOfMonth)

        // Custom 10 days
        val next10Days = BillingCycle.custom(10).calculateNextBillingDate(baseDate, zone)
        val next10DaysDateTime = Instant.ofEpochMilli(next10Days).atZone(zone)
        assertEquals(25, next10DaysDateTime.dayOfMonth)
    }

    @Test
    fun monthEndAnchorDoesNotDrift() {
        val anchor = millis("2026-01-31")
        val feb = BillingCycle.MONTHLY.nextBillingAfter(anchor, anchor, utc)
        val mar = BillingCycle.MONTHLY.nextBillingAfter(anchor, feb, utc)
        val apr = BillingCycle.MONTHLY.nextBillingAfter(anchor, mar, utc)
        assertEquals(
            listOf(LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-30")),
            listOf(date(feb), date(mar), date(apr)),
        )
    }

    @Test
    fun leapDayYearlyAnchorFallsBackToFeb28() {
        val anchor = millis("2028-02-29")
        assertEquals(LocalDate.parse("2029-02-28"), date(BillingCycle.YEARLY.nextBillingAfter(anchor, anchor, utc)))
    }

    @Test
    fun leapDayYearlyAnchorReturnsToFeb29InLeapYear() {
        val anchor = millis("2028-02-29")
        val next = BillingCycle.YEARLY.nextBillingAfter(anchor, millis("2031-02-28"), utc)
        assertEquals(LocalDate.parse("2032-02-29"), date(next))
    }

    @Test
    fun customZeroDaysClampsToOneDay() {
        assertEquals(1, BillingCycle.custom(0).customDays)
    }

    @Test
    fun weeklyNormalizesLikeCustomSevenDays() {
        assertEquals(BillingCycle.custom(7).normalizedMonthlyAmount(10.0), BillingCycle.WEEKLY.normalizedMonthlyAmount(10.0), 1e-9)
    }
}
