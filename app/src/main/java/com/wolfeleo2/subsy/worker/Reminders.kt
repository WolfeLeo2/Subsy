package com.wolfeleo2.subsy.worker

import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.data.util.today
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One alert owed to the user. [key] is stored once shown so it is never repeated. */
data class Reminder(
    val subscription: Subscription,
    val daysLeft: Int,
    val isTrial: Boolean,
    val key: String,
)

/**
 * Alerts for live subscriptions whose trial end (trials) or billing date (active) is 0 to 3 days out:
 * one per stage (3 days, 1 day, day of). A missed worker run still lands in the right stage later.
 */
fun dueReminders(
    subs: List<Subscription>,
    sent: Set<String>,
    today: LocalDate = today(),
): List<Reminder> =
    subs.filter { it.isLive }.mapNotNull { sub ->
        val isTrial = sub.status == SubscriptionStatus.TRIAL
        val target = (if (isTrial) sub.trialEndDate ?: sub.nextBillingDate else sub.nextBillingDate).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, target).toInt()
        if (days !in 0..3) return@mapNotNull null
        val stage = if (days >= 2) 3 else days
        val key = "${sub.id}|$target|$stage"
        if (key in sent) null else Reminder(sub, days, isTrial, key)
    }

/** Drops sent keys whose target date is long past, so the stored set stays small. */
fun pruneSent(
    sent: Set<String>,
    today: LocalDate = today(),
): Set<String> =
    sent
        .filter { key ->
            val date = key.split('|').getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            date != null && !date.isBefore(today.minusDays(1))
        }.toSet()
