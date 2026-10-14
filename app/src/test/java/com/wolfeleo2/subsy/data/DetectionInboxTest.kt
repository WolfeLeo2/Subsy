package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.DetectedKind
import com.wolfeleo2.subsy.data.model.DetectedTransaction
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionSource
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.repository.BillingLedger
import com.wolfeleo2.subsy.data.repository.DetectionInbox
import com.wolfeleo2.subsy.data.repository.SubscriptionMatcher
import com.wolfeleo2.subsy.data.repository.TransactionDetectorRepository
import com.wolfeleo2.subsy.ui.FakePaymentRecordRepository
import com.wolfeleo2.subsy.ui.FakeSubscriptionRepository
import com.wolfeleo2.subsy.ui.FakeTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DetectionInboxTest {
    private val subscriptions = FakeSubscriptionRepository()
    private val payments = FakePaymentRecordRepository()
    private val transactions = FakeTransactionRepository()
    private val detector = FakeDetector()
    private val inbox = DetectionInbox(detector, subscriptions, payments, transactions, BillingLedger(subscriptions, payments))

    private val billingDay = millis(2026, 10, 15)
    private val netflix =
        Subscription(
            id = "netflix",
            name = "Netflix",
            category = Category.ENTERTAINMENT,
            amount = 1100.0,
            currency = "KES",
            billingCycle = BillingCycle.MONTHLY,
            startDate = millis(2026, 1, 15),
            nextBillingDate = billingDay,
        )

    @Test
    fun showsSubscriptionChargesInEveryModeAndTheRestOnlyInAllSpending() =
        runTest {
            subscriptions.insertSubscription(netflix)
            detector.add(detection("n", "NETFLIX.COM", 1100.0))
            detector.add(detection("shop", "Fastmart", 518.0))
            detector.add(detection("pay", "JOHN KAMAU", 500.0, DetectedKind.INCOME))
            detector.add(detection("own", "JANE WANJIRU", 3000.0, DetectedKind.TRANSFER))

            val subsOnly = inbox.pending(flowOf(AppMode.SUBSCRIPTIONS)).first()
            assertEquals(listOf("n"), subsOnly.map { it.detection.id })
            assertEquals("netflix", subsOnly.single().subscription?.id)

            val everything = inbox.pending(flowOf(AppMode.ALL_SPENDING)).first()
            assertEquals(setOf("n", "shop", "pay"), everything.map { it.detection.id }.toSet())
        }

    @Test
    fun loggingASubscriptionChargeGoesThroughTheLedger() =
        runTest {
            subscriptions.insertSubscription(netflix.copy(status = SubscriptionStatus.TRIAL, isFreeTrial = true))
            detector.add(detection("n", "NETFLIX.COM", 1150.0, at = billingDay))

            inbox.log(inbox.pending(flowOf(AppMode.SUBSCRIPTIONS)).first().single())

            val record = payments.getAllPaymentRecords().first().single()
            assertEquals(PaymentStatus.CONFIRMED, record.status)
            assertEquals(1150.0, record.amount, 0.0) // what the bank took, not the listed price
            val after = subscriptions.getSubscriptionByIdDirect("netflix")!!
            assertEquals(SubscriptionStatus.ACTIVE, after.status) // a paid trial has converted
            assertTrue(after.nextBillingDate > billingDay)
            assertTrue(detector.findById("n")!!.isProcessed)
        }

    @Test
    fun confirmsAnAutoLoggedCycleInsteadOfLoggingItTwice() =
        runTest {
            subscriptions.insertSubscription(netflix)
            // The daily worker logged this cycle at 08:00 on the billing day; the SMS came in that afternoon.
            payments.recordPayment(PaymentRecord("auto", "netflix", 1100.0, "KES", billingDay, PaymentStatus.AUTO_LOGGED))
            detector.add(detection("n", "NETFLIX.COM", 1100.0, at = billingDay + 6 * 3_600_000L))

            inbox.log(inbox.pending(flowOf(AppMode.SUBSCRIPTIONS)).first().single())

            val records = payments.getAllPaymentRecords().first()
            assertEquals(1, records.size)
            assertEquals(PaymentStatus.CONFIRMED, records.single().status)
            assertEquals(billingDay, subscriptions.getSubscriptionByIdDirect("netflix")!!.nextBillingDate)
        }

    @Test
    fun otherChargesBecomeTransactionsAndMerchantMemoryBeatsTheModel() =
        runTest {
            transactions.insert(
                Transaction("old", TransactionType.EXPENSE, 400.0, "KES", millis(2026, 9, 1), "SHOPPING", merchant = "Fastmart"),
            )
            detector.add(detection("shop", "Fastmart", 518.0, category = "GROCERIES"))

            inbox.log(inbox.pending(flowOf(AppMode.ALL_SPENDING)).first().single())

            val logged = transactions.getAll().first().first { it.id != "old" }
            assertEquals(518.0, logged.amount, 0.0)
            assertEquals("SHOPPING", logged.categoryKey) // the user's earlier choice, not the model's GROCERIES
            assertEquals(TransactionSource.SMS, logged.source)
        }

    @Test
    fun loggingTwiceLogsOnce() =
        runTest {
            subscriptions.insertSubscription(netflix)
            detector.add(detection("n", "NETFLIX.COM", 1100.0, at = billingDay))
            val charge = inbox.pending(flowOf(AppMode.SUBSCRIPTIONS)).first().single()

            inbox.log(charge) // the card
            inbox.log(charge) // then the notification

            assertEquals(1, payments.getAllPaymentRecords().first().size)
        }

    @Test
    fun suggestsAnUntrackedCatalogServiceEvenInSubscriptionsMode() =
        runTest {
            detector.add(detection("s", "SPOTIFY P2A3B4C5D6", 389.0))

            val charge = inbox.pending(flowOf(AppMode.SUBSCRIPTIONS)).first().single()
            assertEquals("Spotify", charge.suggestion?.name)
            assertEquals("spotify", charge.suggestion?.preset?.id)
            // Nothing to log it against in this mode: the card offers Track it and Ignore only.
            assertEquals(false, charge.loggable)
        }

    @Test
    fun neverSuggestsAServiceTheUserAlreadyHas() =
        runTest {
            subscriptions.insertSubscription(netflix.copy(status = SubscriptionStatus.CANCELLED))
            detector.add(detection("n", "NETFLIX.COM", 1100.0))

            assertTrue(inbox.pending(flowOf(AppMode.SUBSCRIPTIONS)).first().isEmpty())
        }

    @Test
    fun suggestsAMerchantThatChargedASimilarAmountAboutAMonthEarlier() =
        runTest {
            val day = 24 * 3_600_000L
            transactions.insert(
                Transaction("gym", TransactionType.EXPENSE, 2500.0, "KES", billingDay - 30 * day, "HEALTH", merchant = "IRONWORKS GYM"),
            )
            detector.add(detection("g", "IRONWORKS GYM", 2600.0))
            detector.add(detection("f", "Fastmart", 518.0))

            val pending = inbox.pending(flowOf(AppMode.ALL_SPENDING)).first().associateBy { it.detection.id }
            assertEquals("IRONWORKS GYM", pending.getValue("g").suggestion?.name)
            assertNull(pending.getValue("f").suggestion) // seen once: no evidence it repeats
        }

    @Test
    fun theLongestCatalogMatchWins() =
        runTest {
            detector.add(detection("y", "GOOGLE *YouTubePremium", 299.0))
            assertEquals(
                "YouTube Premium",
                inbox
                    .pending(flowOf(AppMode.SUBSCRIPTIONS))
                    .first()
                    .single()
                    .suggestion
                    ?.name,
            )
        }

    @Test
    fun trackingRecordsTheChargeAsTheFirstPaymentWithoutAdvancingAgain() =
        runTest {
            detector.add(detection("n", "NETFLIX.COM", 1100.0, at = billingDay))
            val prefill = inbox.trackPrefill("n")!!
            assertEquals("Netflix", prefill.name)
            assertEquals(1100.0, prefill.amount, 0.0)
            // The add form saves the subscription with its next charge one cycle after the detected one.
            val next = millis(2026, 11, 15)
            val tracked = netflix.copy(startDate = next, nextBillingDate = next)
            subscriptions.insertSubscription(tracked)

            inbox.recordFirstCharge("n", tracked)

            val record = payments.getAllPaymentRecords().first().single()
            assertEquals(PaymentStatus.CONFIRMED, record.status)
            assertEquals(billingDay, record.paymentDate)
            assertEquals(next, subscriptions.getSubscriptionByIdDirect("netflix")!!.nextBillingDate)
            assertTrue(detector.findById("n")!!.isProcessed)
        }

    @Test
    fun aCategoryPickedOnTheCardWins() =
        runTest {
            detector.add(detection("air", "SAFARICOM DATA BUNDLES", 20.0, category = "MOBILE"))
            val charge = inbox.pending(flowOf(AppMode.ALL_SPENDING)).first().single()
            assertEquals("MOBILE", charge.categoryKey)

            inbox.log(charge, categoryKey = "custom:phone")

            assertEquals(
                "custom:phone",
                transactions
                    .getAll()
                    .first()
                    .single()
                    .categoryKey,
            )
        }

    @Test
    fun matchesSubscriptionsByNameIgnoringSpacesAndOnlyWhileLive() {
        val youtube = netflix.copy(id = "yt", name = "YouTube Premium")
        val card = detection("y", "GOOGLE *YouTubePremium", 299.0)
        assertEquals("yt", SubscriptionMatcher.match(card, listOf(netflix, youtube))?.id)
        assertNull(SubscriptionMatcher.match(card, listOf(youtube.copy(status = SubscriptionStatus.PAUSED))))
        assertNull(SubscriptionMatcher.match(detection("s", "SAFARICOM DATA BUNDLES", 20.0), listOf(netflix, youtube)))
    }

    private fun detection(
        id: String,
        merchant: String,
        amount: Double,
        kind: DetectedKind = DetectedKind.EXPENSE,
        at: Long = billingDay,
        category: String? = null,
    ) = DetectedTransaction(
        id = id,
        merchantName = merchant,
        amount = amount,
        rawMessage = "$merchant $amount",
        source = TransactionSource.SMS,
        detectedAt = at,
        currency = "KES",
        kind = kind,
        categoryKey = category,
    )

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
    ) = LocalDate
        .of(year, month, day)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private class FakeDetector : TransactionDetectorRepository {
        private val items = MutableStateFlow<Map<String, DetectedTransaction>>(emptyMap())

        fun add(detection: DetectedTransaction) {
            items.value += detection.id to detection
        }

        override fun getUnprocessedTransactions(): Flow<List<DetectedTransaction>> =
            items.map { all -> all.values.filterNot { it.isProcessed }.sortedByDescending { it.detectedAt } }

        override fun getAllTransactions(): Flow<List<DetectedTransaction>> = items.map { it.values.toList() }

        override fun count(): Flow<Int> = items.map { it.size }

        override suspend fun isDuplicate(
            reference: String?,
            amount: Double,
            currency: String,
            at: Long,
        ) = false

        override suspend fun recordDetectedTransaction(transaction: DetectedTransaction) = add(transaction)

        override suspend fun findById(id: String): DetectedTransaction? = items.value[id]

        override suspend fun markProcessed(transactionId: String) {
            items.value[transactionId]?.let { items.value += transactionId to it.copy(isProcessed = true) }
        }

        override suspend fun clearProcessedTransactions() {
            items.value = items.value.filterValues { !it.isProcessed }
        }
    }
}
