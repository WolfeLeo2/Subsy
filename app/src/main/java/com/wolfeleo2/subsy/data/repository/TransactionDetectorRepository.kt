package com.wolfeleo2.subsy.data.repository

import com.wolfeleo2.subsy.data.local.dao.DetectedTransactionDao
import com.wolfeleo2.subsy.data.local.dao.PaymentRecordDao
import com.wolfeleo2.subsy.data.mapper.toDomain
import com.wolfeleo2.subsy.data.mapper.toEntity
import com.wolfeleo2.subsy.data.model.DetectedTransaction
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface TransactionDetectorRepository {
    fun getUnprocessedTransactions(): Flow<List<DetectedTransaction>>

    fun getAllTransactions(): Flow<List<DetectedTransaction>>

    /** How many charges detection has stored, processed or not. */
    fun count(): Flow<Int>

    /**
     * Whether this charge is already stored: the same reference, or (without one) the same amount and currency within
     * [DUPLICATE_WINDOW_MS]. Banks often report one charge by SMS and by app notification, and SMS can arrive twice.
     */
    suspend fun isDuplicate(
        reference: String?,
        amount: Double,
        currency: String,
        at: Long,
    ): Boolean

    suspend fun recordDetectedTransaction(transaction: DetectedTransaction)

    suspend fun confirmTransaction(
        transactionId: String,
        subscriptionId: String,
        currency: String = "USD",
    ): PaymentRecord?

    suspend fun ignoreTransaction(transactionId: String)

    suspend fun clearProcessedTransactions()
}

class DefaultTransactionDetectorRepository(
    private val detectedTransactionDao: DetectedTransactionDao,
    private val paymentRecordDao: PaymentRecordDao,
) : TransactionDetectorRepository {
    override fun getUnprocessedTransactions(): Flow<List<DetectedTransaction>> =
        detectedTransactionDao.getUnprocessedTransactions().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getAllTransactions(): Flow<List<DetectedTransaction>> =
        detectedTransactionDao.getAllTransactions().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun count(): Flow<Int> = detectedTransactionDao.countAll()

    override suspend fun isDuplicate(
        reference: String?,
        amount: Double,
        currency: String,
        at: Long,
    ): Boolean =
        if (reference != null) {
            detectedTransactionDao.hasReference(reference)
        } else {
            detectedTransactionDao.hasSimilar(amount, currency, at - DUPLICATE_WINDOW_MS, at + DUPLICATE_WINDOW_MS)
        }

    override suspend fun recordDetectedTransaction(transaction: DetectedTransaction) {
        detectedTransactionDao.insertTransaction(transaction.toEntity())
    }

    override suspend fun confirmTransaction(
        transactionId: String,
        subscriptionId: String,
        currency: String,
    ): PaymentRecord? {
        val detected = detectedTransactionDao.getTransactionById(transactionId) ?: return null
        val paymentRecord =
            PaymentRecord(
                id = UUID.randomUUID().toString(),
                subscriptionId = subscriptionId,
                amount = detected.amount,
                currency = currency,
                paymentDate = detected.detectedAt,
                status = PaymentStatus.CONFIRMED,
            )
        paymentRecordDao.insertPaymentRecord(paymentRecord.toEntity())
        detectedTransactionDao.markAsProcessed(transactionId, isProcessed = true)
        return paymentRecord
    }

    override suspend fun ignoreTransaction(transactionId: String) {
        detectedTransactionDao.markAsProcessed(transactionId, isProcessed = true)
    }

    override suspend fun clearProcessedTransactions() {
        detectedTransactionDao.clearProcessedTransactions()
    }
}

const val DUPLICATE_WINDOW_MS = 10 * 60 * 1000L
