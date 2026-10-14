package com.wolfeleo2.subsy.worker

import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.repository.BillingLedger
import com.wolfeleo2.subsy.data.util.toEpochMillis
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.ui.FakePaymentRecordRepository
import com.wolfeleo2.subsy.ui.FakeSubscriptionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerAndRemindersTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun sub(
        id: String = "s",
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        start: LocalDate,
        next: LocalDate,
        trialEnd: LocalDate? = null,
    ) = Subscription(
        id = id,
        name = id,
        category = Category.OTHER,
        amount = 10.0,
        billingCycle = BillingCycle(com.wolfeleo2.subsy.data.model.BillingCycleType.MONTHLY),
        startDate = start.toEpochMillis(),
        nextBillingDate = next.toEpochMillis(),
        status = status,
        trialEndDate = trialEnd?.toEpochMillis(),
    )

    @Test
    fun settleDueCatchesUpEveryMissedCycleAndLeavesFutureAndTrialsAlone() =
        runTest {
            val subs = FakeSubscriptionRepository()
            val payments = FakePaymentRecordRepository()
            subs.insertSubscription(sub("due", start = LocalDate.of(2026, 7, 31), next = LocalDate.of(2026, 7, 31)))
            subs.insertSubscription(sub("future", start = today, next = today.plusDays(5)))
            subs.insertSubscription(sub("trial", SubscriptionStatus.TRIAL, today.minusDays(14), today.minusDays(1)))

            val recorded = BillingLedger(subs, payments).settleDue(today)

            // Jul 31, Aug 31, Sep 30 (anchor-based month ends); next is Oct 31.
            assertEquals(3, recorded)
            val records = payments.getAllPaymentRecords().first()
            assertTrue(records.all { it.subscriptionId == "due" && it.status == PaymentStatus.AUTO_LOGGED })
            assertEquals(LocalDate.of(2026, 10, 31), subs.getSubscriptionByIdDirect("due")!!.nextBillingDate.toLocalDate())
            assertEquals(today.plusDays(5), subs.getSubscriptionByIdDirect("future")!!.nextBillingDate.toLocalDate())
        }

    @Test
    fun remindersFireOncePerStage() {
        val s = sub(start = today.minusMonths(1), next = today.plusDays(3))
        val first = dueReminders(listOf(s), emptySet(), today).single()
        assertEquals(3, first.daysLeft)
        assertTrue(dueReminders(listOf(s), setOf(first.key), today).isEmpty())
        // Two days out is still the 3-day stage, so no repeat.
        assertTrue(dueReminders(listOf(s), setOf(first.key), today.plusDays(1)).isEmpty())
        assertEquals(1, dueReminders(listOf(s), setOf(first.key), today.plusDays(2)).single().daysLeft)
    }

    @Test
    fun trialsAlertOnTrialEndAndFarOrInactiveAreSkipped() {
        val trial = sub("t", SubscriptionStatus.TRIAL, today.minusDays(5), today.plusDays(20), trialEnd = today)
        val far = sub("far", start = today, next = today.plusDays(4))
        val paused = sub("p", SubscriptionStatus.PAUSED, today, today.plusDays(1))
        val result = dueReminders(listOf(trial, far, paused), emptySet(), today)
        assertEquals(listOf("t"), result.map { it.subscription.id })
        assertTrue(result.single().isTrial)
    }

    @Test
    fun pruneDropsOldKeys() {
        val keep = "a|${today.plusDays(2)}|1"
        assertEquals(setOf(keep), pruneSent(setOf(keep, "a|${today.minusDays(9)}|0", "junk"), today))
    }
}
