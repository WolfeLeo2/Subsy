package com.wolfeleo2.subsy.data.repository

import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.data.util.today
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/** The one place a charge is recorded: payment row, trial conversion and the next billing date move together. */
class BillingLedger(
    private val subscriptions: SubscriptionRepository,
    private val payments: PaymentRecordRepository,
) {
    /**
     * Returns the new next billing date. [amount] and [currency] default to the subscription's price; a detected charge
     * passes what the bank actually took.
     */
    suspend fun logPaymentAndAdvance(
        sub: Subscription,
        status: PaymentStatus,
        paidAt: Long = System.currentTimeMillis(),
        amount: Double = sub.amount,
        currency: String = sub.currency,
    ): Long {
        payments.recordPayment(
            PaymentRecord(
                id = UUID.randomUUID().toString(),
                subscriptionId = sub.id,
                amount = amount,
                currency = currency,
                paymentDate = paidAt,
                status = status,
            ),
        )
        // A paid trial has converted.
        if (sub.status == SubscriptionStatus.TRIAL) {
            subscriptions.updateSubscription(sub.copy(status = SubscriptionStatus.ACTIVE))
        }
        return subscriptions.advanceToNextBillingCycle(sub.id) ?: sub.nextBillingAfterCurrent()
    }

    /**
     * Auto-logs every cycle of an ACTIVE subscription whose billing date has arrived, one record per missed
     * cycle dated on the billing date. Trials are left alone: converting one is the user's call.
     * Returns the number of payments recorded.
     */
    suspend fun settleDue(today: LocalDate = today()): Int {
        var recorded = 0
        subscriptions
            .getAllSubscriptions()
            .first()
            .filter { it.status == SubscriptionStatus.ACTIVE }
            .forEach { listed ->
                var sub: Subscription? = listed
                while (sub != null && !sub.nextBillingDate.toLocalDate().isAfter(today)) {
                    logPaymentAndAdvance(sub, PaymentStatus.AUTO_LOGGED, sub.nextBillingDate)
                    recorded++
                    sub = subscriptions.getSubscriptionByIdDirect(sub.id)
                }
            }
        return recorded
    }
}
