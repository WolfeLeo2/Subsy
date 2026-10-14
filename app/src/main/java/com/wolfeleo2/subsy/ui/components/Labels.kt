package com.wolfeleo2.subsy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.BillingCycleType
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.ui.util.FormattingUtils

@Composable
fun Category.label(): String =
    stringResource(
        when (this) {
            Category.ENTERTAINMENT -> R.string.category_entertainment
            Category.WORK_SAAS -> R.string.category_work
            Category.HEALTH_FITNESS -> R.string.category_health
            Category.UTILITIES -> R.string.category_utilities
            Category.PERSONAL -> R.string.category_personal
            Category.OTHER -> R.string.category_other
        },
    )

@Composable
fun BillingCycleType.label(): String =
    stringResource(
        when (this) {
            BillingCycleType.WEEKLY -> R.string.cycle_weekly
            BillingCycleType.MONTHLY -> R.string.cycle_monthly
            BillingCycleType.QUARTERLY -> R.string.cycle_quarterly
            BillingCycleType.BI_ANNUALLY -> R.string.cycle_biannually
            BillingCycleType.YEARLY -> R.string.cycle_yearly
            BillingCycleType.CUSTOM -> R.string.cycle_custom
        },
    )

@Composable
fun BillingCycle.label(): String {
    val days = customDays ?: 30
    return if (type == BillingCycleType.CUSTOM) pluralStringResource(R.plurals.cycle_every_days, days, days) else type.label()
}

/** Short per-period suffix shown after an amount: "/mo", "/yr", "/14 d". */
@Composable
fun BillingCycle.suffix(): String {
    val days = customDays ?: 30
    return when (type) {
        BillingCycleType.WEEKLY -> stringResource(R.string.suffix_week)
        BillingCycleType.MONTHLY -> stringResource(R.string.suffix_month)
        BillingCycleType.QUARTERLY -> stringResource(R.string.suffix_quarter)
        BillingCycleType.BI_ANNUALLY -> stringResource(R.string.suffix_half_year)
        BillingCycleType.YEARLY -> stringResource(R.string.suffix_year)
        BillingCycleType.CUSTOM -> stringResource(R.string.suffix_days, days)
    }
}

/**
 * The one line that says what happens next: "Trial ends in 2 days", "Renews Oct 14", "Paused".
 * Wording differs for trials and renewals so a trial never reads "Renews tomorrow".
 */
@Composable
fun Subscription.nextEventLabel(): String {
    val trialEnd = trialEndDate
    return when (status) {
        SubscriptionStatus.PAUSED -> stringResource(R.string.status_paused)
        SubscriptionStatus.CANCELLED -> stringResource(R.string.status_cancelled)
        SubscriptionStatus.TRIAL if trialEnd != null -> {
            val days = FormattingUtils.daysUntil(trialEnd).toInt()
            when {
                days < 0 -> stringResource(R.string.trial_ended)
                days == 0 -> stringResource(R.string.trial_ends_today)
                days == 1 -> stringResource(R.string.trial_ends_tomorrow)
                else -> pluralStringResource(R.plurals.trial_ends_in_days, days, days)
            }
        }
        else -> {
            val days = FormattingUtils.daysUntil(nextBillingDate).toInt()
            when {
                days < 0 -> pluralStringResource(R.plurals.charge_overdue_days, -days, -days)
                days == 0 -> stringResource(R.string.renews_today)
                days == 1 -> stringResource(R.string.renews_tomorrow)
                days <= 7 -> pluralStringResource(R.plurals.renews_in_days, days, days)
                else -> stringResource(R.string.renews_on, FormattingUtils.formatMonthDay(nextBillingDate))
            }
        }
    }
}

/** Trials ending within three days, or charges already overdue, need the user now. */
val Subscription.isUrgent: Boolean
    get() =
        when (status) {
            SubscriptionStatus.TRIAL -> trialEndDate?.let { FormattingUtils.daysUntil(it) in 0..3 } ?: false
            SubscriptionStatus.ACTIVE -> FormattingUtils.daysUntil(nextBillingDate) < 0
            else -> false
        }
