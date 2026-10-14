package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.local.dao.PaymentRecordDao
import com.wolfeleo2.subsy.data.local.entity.PaymentRecordEntity
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.repository.DefaultPaymentRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaymentRecordRepositoryTest {
    private class FakePaymentRecordDao : PaymentRecordDao {
        val records = mutableMapOf<String, PaymentRecordEntity>()
        private val flow = MutableStateFlow<List<PaymentRecordEntity>>(emptyList())

        private fun emit() {
            flow.value = records.values.toList()
        }

        override fun getPaymentRecordsForSubscription(subscriptionId: String): Flow<List<PaymentRecordEntity>> =
            flow.map { list ->
                list.filter {
                    it.subscriptionId == subscriptionId
                }
            }

        override fun getAllPaymentRecords(): Flow<List<PaymentRecordEntity>> = flow

        override fun getPaymentRecordsBetween(
            startDate: Long,
            endDate: Long,
        ): Flow<List<PaymentRecordEntity>> =
            flow.map { list ->
                list.filter {
                    it.paymentDate in startDate..endDate
                }
            }

        override fun getTotalSpentForSubscription(subscriptionId: String): Flow<Double?> =
            flow.map { list ->
                val sum = list.filter { it.subscriptionId == subscriptionId }.sumOf { it.amount }
                if (sum == 0.0) null else sum
            }

        override fun getTotalSpentAllTime(): Flow<Double?> =
            flow.map { list ->
                val sum = list.sumOf { it.amount }
                if (sum == 0.0) null else sum
            }

        override suspend fun insertPaymentRecord(record: PaymentRecordEntity) {
            records[record.id] = record
            emit()
        }

        override suspend fun insertPaymentRecords(records: List<PaymentRecordEntity>) {
            records.forEach { this.records[it.id] = it }
            emit()
        }

        override suspend fun deletePaymentRecord(record: PaymentRecordEntity) {
            records.remove(record.id)
            emit()
        }

        override suspend fun deletePaymentRecordsForSubscription(subscriptionId: String) {
            val toRemove = records.values.filter { it.subscriptionId == subscriptionId }.map { it.id }
            toRemove.forEach { records.remove(it) }
            emit()
        }
    }

    private lateinit var fakeDao: FakePaymentRecordDao
    private lateinit var repository: DefaultPaymentRecordRepository

    @Before
    fun setup() {
        fakeDao = FakePaymentRecordDao()
        repository = DefaultPaymentRecordRepository(fakeDao)
    }

    @Test
    fun recordPaymentAndRetrieveBySubscription() =
        runTest {
            val record =
                PaymentRecord(
                    id = "pay-1",
                    subscriptionId = "sub-100",
                    amount = 14.99,
                    currency = "USD",
                    paymentDate = 1000L,
                    status = PaymentStatus.AUTO_LOGGED,
                )

            repository.recordPayment(record)

            val records = repository.getPaymentRecordsForSubscription("sub-100").first()
            assertEquals(1, records.size)
            assertEquals("pay-1", records.first().id)
            assertEquals(14.99, records.first().amount, 0.001)
        }

    @Test
    fun totalSpentCalculationsFlow() =
        runTest {
            repository.recordPayment(
                PaymentRecord("p1", "sub-1", 10.0, "USD", 1000L, PaymentStatus.CONFIRMED),
            )
            repository.recordPayment(
                PaymentRecord("p2", "sub-1", 15.0, "USD", 2000L, PaymentStatus.AUTO_LOGGED),
            )
            repository.recordPayment(
                PaymentRecord("p3", "sub-2", 20.0, "USD", 3000L, PaymentStatus.CONFIRMED),
            )

            val totalSub1 = repository.getTotalSpentForSubscription("sub-1").first()
            assertEquals(25.0, totalSub1, 0.001)

            val totalAll = repository.getTotalSpentAllTime().first()
            assertEquals(45.0, totalAll, 0.001)
        }

    @Test
    fun deletePaymentsForSubscription() =
        runTest {
            repository.recordPayment(PaymentRecord("p1", "sub-1", 10.0))
            repository.recordPayment(PaymentRecord("p2", "sub-1", 15.0))
            repository.recordPayment(PaymentRecord("p3", "sub-2", 20.0))

            repository.deletePaymentRecordsForSubscription("sub-1")

            val sub1Records = repository.getPaymentRecordsForSubscription("sub-1").first()
            assertTrue(sub1Records.isEmpty())

            val sub2Records = repository.getPaymentRecordsForSubscription("sub-2").first()
            assertEquals(1, sub2Records.size)
        }
}
