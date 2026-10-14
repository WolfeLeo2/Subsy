package com.wolfeleo2.subsy.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class BillingCycleType(
    val displayName: String,
) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    BI_MONTHLY("Every 2 months"),
    QUARTERLY("Quarterly"),
    BI_ANNUALLY("Bi-Annually"),
    YEARLY("Yearly"),
    CUSTOM("Custom"),
}

data class BillingCycle(
    val type: BillingCycleType,
    val customDays: Int? = null,
) {
    private val days: Int get() = (customDays ?: 30).coerceAtLeast(1)

    val serializedName: String
        get() =
            when (type) {
                BillingCycleType.CUSTOM -> "CUSTOM_$days"
                else -> type.name
            }

    fun calculateNextBillingDate(
        fromEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = plusCycles(fromEpochMillis, 1, zoneId)

    /**
     * First billing date strictly after [afterEpochMillis], counted in whole cycles from [anchorEpochMillis].
     * Counting from the anchor keeps month-end dates stable (Jan 31 -> Feb 28 -> Mar 31).
     */
    fun nextBillingAfter(
        anchorEpochMillis: Long,
        afterEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        var n = 1L
        var next = plusCycles(anchorEpochMillis, n, zoneId)
        while (next <= afterEpochMillis) next = plusCycles(anchorEpochMillis, ++n, zoneId)
        return next
    }

    private fun plusCycles(
        anchorEpochMillis: Long,
        n: Long,
        zoneId: ZoneId,
    ): Long {
        val anchor = Instant.ofEpochMilli(anchorEpochMillis).atZone(zoneId)
        val next: ZonedDateTime =
            when (type) {
                BillingCycleType.WEEKLY -> anchor.plusWeeks(n)
                BillingCycleType.MONTHLY -> anchor.plusMonths(n)
                BillingCycleType.BI_MONTHLY -> anchor.plusMonths(2 * n)
                BillingCycleType.QUARTERLY -> anchor.plusMonths(3 * n)
                BillingCycleType.BI_ANNUALLY -> anchor.plusMonths(6 * n)
                BillingCycleType.YEARLY -> anchor.plusYears(n)
                BillingCycleType.CUSTOM -> anchor.plusDays(days * n)
            }
        return next.toInstant().toEpochMilli()
    }

    /**
     * Calculates the normalized monthly cost for burn-rate computations.
     */
    fun normalizedMonthlyAmount(amount: Double): Double =
        when (type) {
            BillingCycleType.WEEKLY -> amount * DAYS_PER_MONTH / 7.0
            BillingCycleType.MONTHLY -> amount
            BillingCycleType.BI_MONTHLY -> amount / 2.0
            BillingCycleType.QUARTERLY -> amount / 3.0
            BillingCycleType.BI_ANNUALLY -> amount / 6.0
            BillingCycleType.YEARLY -> amount / 12.0
            BillingCycleType.CUSTOM -> amount * DAYS_PER_MONTH / days
        }

    companion object {
        private const val DAYS_PER_MONTH = 365.25 / 12
        val WEEKLY = BillingCycle(BillingCycleType.WEEKLY)
        val MONTHLY = BillingCycle(BillingCycleType.MONTHLY)
        val BI_MONTHLY = BillingCycle(BillingCycleType.BI_MONTHLY)
        val QUARTERLY = BillingCycle(BillingCycleType.QUARTERLY)
        val BI_ANNUALLY = BillingCycle(BillingCycleType.BI_ANNUALLY)
        val YEARLY = BillingCycle(BillingCycleType.YEARLY)

        fun custom(days: Int): BillingCycle = BillingCycle(BillingCycleType.CUSTOM, days.coerceAtLeast(1))

        fun fromSerialized(raw: String): BillingCycle {
            val trimmed = raw.trim()
            return when {
                trimmed.startsWith("CUSTOM_") -> {
                    val days = trimmed.removePrefix("CUSTOM_").toIntOrNull() ?: 30
                    custom(days)
                }
                trimmed.equals("WEEKLY", ignoreCase = true) -> WEEKLY
                trimmed.equals("MONTHLY", ignoreCase = true) -> MONTHLY
                trimmed.equals("BI_MONTHLY", ignoreCase = true) -> BI_MONTHLY
                trimmed.equals("QUARTERLY", ignoreCase = true) -> QUARTERLY
                trimmed.equals("BI_ANNUALLY", ignoreCase = true) || trimmed.equals("BIANNUALLY", ignoreCase = true) -> BI_ANNUALLY
                trimmed.equals("YEARLY", ignoreCase = true) -> YEARLY
                else -> MONTHLY
            }
        }
    }
}
