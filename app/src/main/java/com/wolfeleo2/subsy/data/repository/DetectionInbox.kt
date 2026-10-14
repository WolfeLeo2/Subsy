package com.wolfeleo2.subsy.data.repository

import com.wolfeleo2.subsy.data.catalog.PresetServiceCatalog
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.DetectedKind
import com.wolfeleo2.subsy.data.model.DetectedTransaction
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.PresetService
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** A subscription the user doesn't track that a charge looks like: a catalog service, or a charge that repeats monthly. */
data class SubscriptionSuggestion(
    val name: String,
    /** The catalog entry when the merchant is a known service; null for a repeat found in history. */
    val preset: PresetService?,
)

/** A detection the user can act on, and what each action would do. */
data class SpottedCharge(
    val detection: DetectedTransaction,
    /** The live subscription this charge pays; Log it records a payment against it. */
    val subscription: Subscription?,
    /** Offered as Track it when the charge matches nothing tracked but looks like a subscription. */
    val suggestion: SubscriptionSuggestion? = null,
    /** Whether Log it is offered: a subscription payment in any mode, a transaction only in All spending. */
    val loggable: Boolean = true,
    /** The category Log it gives a transaction unless the user picks another; null when it logs a payment. */
    val categoryKey: String? = null,
)

/** How a logged transaction's note names the other side: "Sent to John Kamau", "Paid to Fastmart", "Received from…". */
enum class ChargeNote {
    SENT_TO,
    PAID_TO,
    RECEIVED_FROM,
    ;

    companion object {
        /**
         * From the message's own words: money to a person ("sent to", "transferred") is Sent to, anything else Paid to,
         * including M-Pesa card payments that say "sent to M-PESA CARD for account NETFLIX.COM".
         */
        fun of(detection: DetectedTransaction): ChargeNote {
            if (detection.kind == DetectedKind.INCOME) return RECEIVED_FROM
            val text = detection.rawMessage.lowercase()
            val toPerson = ("sent to" in text || "transferred" in text) && "for account" !in text
            return if (toPerson) SENT_TO else PAID_TO
        }
    }
}

/** What "Track it" fills the add form with, from a spotted charge. */
data class TrackPrefill(
    val name: String,
    val preset: PresetService?,
    val amount: Double,
    val currency: String,
    /** When the bank took the charge; the subscription's next charge is one cycle after it. */
    val chargedAt: Long,
)

/**
 * Turns detected charges into records, only when the user taps. A charge matching a live subscription is logged
 * through [BillingLedger] (payment row, trial conversion, next billing date); any other charge becomes a [Transaction]
 * in All spending mode; a charge that looks like an untracked subscription can start one (Track it).
 */
class DetectionInbox(
    private val detector: TransactionDetectorRepository,
    private val subscriptions: SubscriptionRepository,
    private val payments: PaymentRecordRepository,
    private val transactions: TransactionRepository,
    private val ledger: BillingLedger,
    /** The note text for a logged transaction, from string resources (this class has no context). */
    private val describe: (ChargeNote, counterparty: String) -> String,
) {
    /**
     * Unprocessed detections worth showing in [mode]: subscription charges and subscription suggestions always, other
     * expenses and income only in All spending. Transfers between the user's own accounts are never shown.
     */
    fun pending(mode: Flow<AppMode?>): Flow<List<SpottedCharge>> =
        combine(
            detector.getAllTransactions(),
            subscriptions.getAllSubscriptions(),
            transactions.getAll(),
            mode,
        ) { all, subs, txns, appMode ->
            all.filterNot { it.isProcessed }.mapNotNull { spot(it, subs, all, txns, appMode) }
        }

    /** The same rule for one detection, for the notification posted right after it is stored. */
    suspend fun spot(
        detection: DetectedTransaction,
        mode: AppMode?,
    ): SpottedCharge? =
        spot(
            detection,
            subscriptions.getAllSubscriptions().first(),
            detector.getAllTransactions().first(),
            transactions.getAll().first(),
            mode,
        )

    /**
     * Logs [charge] once, with [categoryKey] if the user picked one: a second tap (card, then notification) finds it
     * processed and does nothing.
     */
    suspend fun log(
        charge: SpottedCharge,
        categoryKey: String? = null,
    ) {
        val detection = charge.detection
        if (!charge.loggable || detector.findById(detection.id)?.isProcessed != false) return
        val subscription = charge.subscription
        if (subscription != null) {
            payFor(subscription, detection)
        } else {
            addTransaction(detection, categoryKey ?: proposedCategory(detection))
        }
        detector.markProcessed(detection.id)
    }

    /** For the notification's Log it action, which only carries the id. */
    suspend fun logById(
        detectionId: String,
        mode: AppMode?,
    ) {
        val detection = detector.findById(detectionId) ?: return
        spot(detection, mode)?.let { log(it) }
    }

    suspend fun ignore(detectionId: String) = detector.markProcessed(detectionId)

    /** The add form's starting point for Track it; null if the charge is gone or already handled. */
    suspend fun trackPrefill(detectionId: String): TrackPrefill? {
        val detection = detector.findById(detectionId)?.takeUnless { it.isProcessed } ?: return null
        val suggestion = spot(detection, AppMode.ALL_SPENDING)?.suggestion
        return TrackPrefill(
            name = suggestion?.name ?: detection.merchantName,
            preset = suggestion?.preset,
            amount = detection.amount,
            currency = detection.currency,
            chargedAt = detection.detectedAt,
        )
    }

    /**
     * The user started tracking [subscription] from this charge. The form already set its next charge one cycle after
     * the detected one, so the charge is recorded as a confirmed payment directly; going through the ledger would
     * advance the billing date a second time.
     */
    suspend fun recordFirstCharge(
        detectionId: String,
        subscription: Subscription,
    ) {
        val detection = detector.findById(detectionId)?.takeUnless { it.isProcessed } ?: return
        payments.recordPayment(
            PaymentRecord(
                id = UUID.randomUUID().toString(),
                subscriptionId = subscription.id,
                amount = detection.amount,
                currency = detection.currency,
                paymentDate = detection.detectedAt,
                status = PaymentStatus.CONFIRMED,
            ),
        )
        detector.markProcessed(detectionId)
    }

    private suspend fun spot(
        detection: DetectedTransaction,
        subs: List<Subscription>,
        history: List<DetectedTransaction>,
        txns: List<Transaction>,
        mode: AppMode?,
    ): SpottedCharge? {
        if (detection.kind == DetectedKind.TRANSFER) return null
        val subscription = if (detection.kind == DetectedKind.EXPENSE) SubscriptionMatcher.match(detection, subs) else null
        val suggestion =
            if (subscription == null && detection.kind == DetectedKind.EXPENSE) {
                SubscriptionSuggester.suggest(detection, subs, history, txns)
            } else {
                null
            }
        val loggable = subscription != null || mode == AppMode.ALL_SPENDING
        if (!loggable && suggestion == null) return null
        val category = if (subscription == null && loggable) proposedCategory(detection) else null
        return SpottedCharge(detection, subscription, suggestion, loggable, category)
    }

    /** Merchant memory first (what the user picked last time for this merchant), then the model's guess, then Other. */
    private suspend fun proposedCategory(detection: DetectedTransaction): String {
        val type = detection.transactionType()
        return detection.merchantName.takeIf { it.isNotBlank() }?.let { transactions.rememberedCategory(it, type) }
            ?: detection.categoryKey?.takeIf { SpendCategory.fromKey(it)?.kind == type }
            ?: SpendCategory.fallbackFor(type).name
    }

    private suspend fun payFor(
        subscription: Subscription,
        detection: DetectedTransaction,
    ) {
        // The daily worker logs a due cycle on its billing date. If it already did for this charge, confirm that record
        // with what the bank took instead of logging (and advancing) a second time.
        val autoLogged =
            payments.getPaymentRecordsForSubscription(subscription.id).first().firstOrNull {
                it.status == PaymentStatus.AUTO_LOGGED && abs(it.paymentDate - detection.detectedAt) <= RECONCILE_WINDOW_MS
            }
        if (autoLogged != null) {
            payments.recordPayment(
                autoLogged.copy(status = PaymentStatus.CONFIRMED, amount = detection.amount, currency = detection.currency),
            )
        } else {
            ledger.logPaymentAndAdvance(
                subscription,
                PaymentStatus.CONFIRMED,
                paidAt = detection.detectedAt,
                amount = detection.amount,
                currency = detection.currency,
            )
        }
    }

    private suspend fun addTransaction(
        detection: DetectedTransaction,
        categoryKey: String,
    ) {
        transactions.insert(
            Transaction(
                id = UUID.randomUUID().toString(),
                type = detection.transactionType(),
                amount = detection.amount,
                currency = detection.currency,
                date = detection.detectedAt,
                categoryKey = categoryKey,
                merchant = detection.merchantName.takeIf { it.isNotBlank() },
                // Transaction rows show the note before the category, so "Paid to Fastmart" beats a bare "Groceries".
                // Airtime and bundles name nobody, so they get no note and the row shows the category.
                note = detection.merchantName.takeIf { it.isNotBlank() }?.let { describe(ChargeNote.of(detection), it) },
                source = detection.source,
            ),
        )
    }

    private fun DetectedTransaction.transactionType() = if (kind == DetectedKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE

    private companion object {
        const val RECONCILE_WINDOW_MS = 3 * 24 * 60 * 60 * 1000L
    }
}

/**
 * Which live subscription a detected charge pays, by name in code: the subscription's full name, the catalog keywords
 * of a preset service with that name, or its first word when that word is long enough to be specific. Spaces and
 * punctuation are ignored ("YouTube Premium" matches "GOOGLE *YouTubePremium"). Several matches: closest amount wins.
 */
object SubscriptionMatcher {
    private const val MIN_WORD_LENGTH = 4

    fun match(
        detection: DetectedTransaction,
        subscriptions: List<Subscription>,
    ): Subscription? {
        val haystack = squash(detection.merchantName.ifBlank { detection.rawMessage })
        if (haystack.isEmpty()) return null
        return subscriptions
            .filter { it.isLive && keysFor(it).any { key -> key in haystack } }
            .minByOrNull { if (it.currency == detection.currency) abs(it.amount - detection.amount) else Double.MAX_VALUE }
    }

    private fun keysFor(subscription: Subscription): List<String> {
        val preset = PresetServiceCatalog.presets.firstOrNull { it.name.equals(subscription.name, ignoreCase = true) }
        val firstWord =
            subscription.name
                .split(' ')
                .first()
                .takeIf { it.length >= MIN_WORD_LENGTH }
        return (listOfNotNull(subscription.name, firstWord) + preset?.keywords.orEmpty()).map(::squash).filter { it.isNotEmpty() }
    }
}

/**
 * Whether an unmatched charge looks like a subscription the user doesn't track, from evidence the app has rather than
 * a guess: the merchant is a catalog service (longest matching name or keyword wins, so "GOOGLE *YouTubePremium" is
 * YouTube Premium rather than Google One), or the same merchant took a similar amount 25 to 35 days earlier. A service
 * the user already has under any status (paused, cancelled) is never suggested again.
 */
object SubscriptionSuggester {
    private const val MIN_KEY_LENGTH = 4
    private const val AMOUNT_TOLERANCE = 0.15
    private val repeatDays = 25L..35L

    fun suggest(
        detection: DetectedTransaction,
        subscriptions: List<Subscription>,
        history: List<DetectedTransaction>,
        transactions: List<Transaction>,
    ): SubscriptionSuggestion? {
        val tracked = subscriptions.map { squash(it.name) }.toSet()
        val haystack = squash(detection.merchantName.ifBlank { detection.rawMessage })
        val preset =
            PresetServiceCatalog.presets
                .filter { squash(it.name) !in tracked }
                .mapNotNull { preset -> keysFor(preset).filter { it in haystack }.maxByOrNull { it.length }?.let { preset to it.length } }
                .maxByOrNull { it.second }
                ?.first
        if (preset != null) return SubscriptionSuggestion(preset.name, preset)

        val merchant = detection.merchantName.takeIf { it.isNotBlank() } ?: return null
        if (squash(merchant) in tracked) return null
        val earlier =
            history
                .filter {
                    it.id != detection.id &&
                        it.merchantName.equals(
                            merchant,
                            ignoreCase = true,
                        )
                }.map { it.amount to it.detectedAt } +
                transactions
                    .filter { it.type == TransactionType.EXPENSE && it.merchant.equals(merchant, ignoreCase = true) }
                    .map { it.amount to it.date }
        val repeats =
            earlier.any { (amount, at) ->
                TimeUnit.MILLISECONDS.toDays(detection.detectedAt - at) in repeatDays &&
                    abs(amount - detection.amount) <= detection.amount * AMOUNT_TOLERANCE
            }
        return if (repeats) SubscriptionSuggestion(merchant, null) else null
    }

    private fun keysFor(preset: PresetService) =
        (listOf(preset.name) + preset.keywords).map(::squash).filter { it.length >= MIN_KEY_LENGTH }
}

/** Lowercase letters and digits only, so spacing and punctuation never decide a match. */
private fun squash(text: String) = text.lowercase().filter(Char::isLetterOrDigit)
