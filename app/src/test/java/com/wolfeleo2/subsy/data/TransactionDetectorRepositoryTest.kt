package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.local.dao.DetectedTransactionDao
import com.wolfeleo2.subsy.data.local.dao.PaymentRecordDao
import com.wolfeleo2.subsy.data.local.entity.DetectedTransactionEntity
import com.wolfeleo2.subsy.data.local.entity.PaymentRecordEntity
import com.wolfeleo2.subsy.data.model.DetectedTransaction
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.TransactionSource
import com.wolfeleo2.subsy.data.repository.DefaultTransactionDetectorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionDetectorRepositoryTest {
    private class FakeDetectedTransactionDao : DetectedTransactionDao {
        val storage = mutableMapOf<String, DetectedTransactionEntity>()
        private val flow = MutableStateFlow<List<DetectedTransactionEntity>>(emptyList())

        private fun emit() {
            flow.value = storage.values.toList()
        }

        override fun getUnprocessedTransactions(): Flow<List<DetectedTransactionEntity>> =
            flow.map { list -> list.filter { !it.isProcessed } }

        override fun getAllTransactions(): Flow<List<DetectedTransactionEntity>> = flow

        override suspend fun getTransactionById(id: String): DetectedTransactionEntity? = storage[id]

        override suspend fun insertTransaction(transaction: DetectedTransactionEntity) {
            storage[transaction.id] = transaction
            emit()
        }

        override suspend fun markAsProcessed(
            id: String,
            isProcessed: Boolean,
        ) {
            storage[id]?.let {
                storage[id] = it.copy(isProcessed = isProcessed)
                emit()
            }
        }

        override suspend fun deleteTransaction(transaction: DetectedTransactionEntity) {
            storage.remove(transaction.id)
            emit()
        }

        override suspend fun clearProcessedTransactions() {
            val toRemove = storage.values.filter { it.isProcessed }.map { it.id }
            toRemove.forEach { storage.remove(it) }
            emit()
        }

        override fun countAll(): Flow<Int> = flow.map { it.size }

        override suspend fun hasReference(reference: String): Boolean = storage.values.any { it.reference == reference }

        override suspend fun hasSimilar(
            amount: Double,
            currency: String,
            from: Long,
            to: Long,
        ): Boolean = storage.values.any { it.amount == amount && it.currency == currency && it.detectedAt in from..to }
    }

    private class FakePaymentRecordDao : PaymentRecordDao {
        val storage = mutableMapOf<String, PaymentRecordEntity>()

        override fun getPaymentRecordsForSubscription(subscriptionId: String): Flow<List<PaymentRecordEntity>> =
            MutableStateFlow(emptyList())

        override fun getAllPaymentRecords(): Flow<List<PaymentRecordEntity>> = MutableStateFlow(storage.values.toList())

        override fun getPaymentRecordsBetween(
            startDate: Long,
            endDate: Long,
        ): Flow<List<PaymentRecordEntity>> = MutableStateFlow(emptyList())

        override fun getTotalSpentForSubscription(subscriptionId: String): Flow<Double?> = MutableStateFlow(null)

        override fun getTotalSpentAllTime(): Flow<Double?> = MutableStateFlow(null)

        override suspend fun insertPaymentRecord(record: PaymentRecordEntity) {
            storage[record.id] = record
        }

        override suspend fun insertPaymentRecords(records: List<PaymentRecordEntity>) {
            records.forEach { storage[it.id] = it }
        }

        override suspend fun deletePaymentRecord(record: PaymentRecordEntity) {
            storage.remove(record.id)
        }

        override suspend fun deletePaymentRecordsForSubscription(subscriptionId: String) {}
    }

    private lateinit var fakeTxDao: FakeDetectedTransactionDao
    private lateinit var fakePaymentDao: FakePaymentRecordDao
    private lateinit var repository: DefaultTransactionDetectorRepository

    @Before
    fun setup() {
        fakeTxDao = FakeDetectedTransactionDao()
        fakePaymentDao = FakePaymentRecordDao()
        repository = DefaultTransactionDetectorRepository(fakeTxDao, fakePaymentDao)
    }

    @Test
    fun recordAndRetrieveUnprocessedTransactions() =
        runTest {
            val tx =
                DetectedTransaction(
                    id = "tx-1",
                    merchantName = "Spotify",
                    amount = 11.99,
                    rawMessage = "Bank Alert: Debit of $11.99 at Spotify",
                    source = TransactionSource.SMS,
                    detectedAt = 1000L,
                    isProcessed = false,
                )

            repository.recordDetectedTransaction(tx)

            val unprocessed = repository.getUnprocessedTransactions().first()
            assertEquals(1, unprocessed.size)
            assertEquals("Spotify", unprocessed.first().merchantName)
        }

    @Test
    fun confirmTransactionCreatesPaymentRecordAndMarksProcessed() =
        runTest {
            val tx =
                DetectedTransaction(
                    id = "tx-100",
                    merchantName = "Netflix",
                    amount = 15.49,
                    rawMessage = "Charged $15.49 by Netflix",
                    source = TransactionSource.NOTIFICATION,
                    detectedAt = 5000L,
                )
            repository.recordDetectedTransaction(tx)

            val payment = repository.confirmTransaction("tx-100", "sub-netflix", "USD")
            assertNotNull(payment)
            assertEquals("sub-netflix", payment?.subscriptionId)
            assertEquals(15.49, payment?.amount ?: 0.0, 0.001)
            assertEquals(PaymentStatus.CONFIRMED, payment?.status)

            // Payment record saved in payment DAO
            assertTrue(fakePaymentDao.storage.isNotEmpty())

            // Transaction marked as processed
            val unprocessed = repository.getUnprocessedTransactions().first()
            assertTrue(unprocessed.isEmpty())
        }

    @Test
    fun sameReferenceOrSameAmountSoonAfterIsADuplicate() =
        runTest {
            repository.recordDetectedTransaction(
                DetectedTransaction(
                    id = "sms",
                    merchantName = "NETFLIX.COM",
                    amount = 1100.0,
                    rawMessage = "UJE1NB2XYZ Confirmed. Ksh1,100.00 sent to M-PESA CARD for account NETFLIX.COM",
                    source = TransactionSource.SMS,
                    detectedAt = 0L,
                    currency = "KES",
                    reference = "UJE1NB2XYZ",
                ),
            )
            assertTrue(repository.isDuplicate("UJE1NB2XYZ", 1100.0, "KES", 3_600_000L))
            assertFalse(repository.isDuplicate("OTHERREF01", 1100.0, "KES", 60_000L))
            assertTrue(repository.isDuplicate(null, 1100.0, "KES", 9 * 60_000L))
            assertFalse(repository.isDuplicate(null, 1100.0, "KES", 11 * 60_000L))
            assertFalse(repository.isDuplicate(null, 1100.0, "USD", 60_000L))
        }

    @Test
    fun ignoreTransactionMarksItAsProcessed() =
        runTest {
            val tx =
                DetectedTransaction(
                    id = "tx-200",
                    merchantName = "Unknown Store",
                    amount = 5.00,
                    rawMessage = "Charged $5.00",
                    source = TransactionSource.SMS,
                )
            repository.recordDetectedTransaction(tx)

            repository.ignoreTransaction("tx-200")

            val unprocessed = repository.getUnprocessedTransactions().first()
            assertTrue(unprocessed.isEmpty())
        }
}
