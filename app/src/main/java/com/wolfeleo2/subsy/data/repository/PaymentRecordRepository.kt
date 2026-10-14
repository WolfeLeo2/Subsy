package com.wolfeleo2.subsy.data.repository

import com.wolfeleo2.subsy.data.local.dao.PaymentRecordDao
import com.wolfeleo2.subsy.data.mapper.toDomain
import com.wolfeleo2.subsy.data.mapper.toEntity
import com.wolfeleo2.subsy.data.model.PaymentRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PaymentRecordRepository {
    fun getPaymentRecordsForSubscription(subscriptionId: String): Flow<List<PaymentRecord>>

    fun getAllPaymentRecords(): Flow<List<PaymentRecord>>

    fun getPaymentRecordsBetween(
        startDate: Long,
        endDate: Long,
    ): Flow<List<PaymentRecord>>

    fun getTotalSpentForSubscription(subscriptionId: String): Flow<Double>

    fun getTotalSpentAllTime(): Flow<Double>

    suspend fun recordPayment(paymentRecord: PaymentRecord)

    suspend fun deletePayment(paymentRecord: PaymentRecord)

    suspend fun deletePaymentRecordsForSubscription(subscriptionId: String)
}

class DefaultPaymentRecordRepository(
    private val paymentRecordDao: PaymentRecordDao,
) : PaymentRecordRepository {
    override fun getPaymentRecordsForSubscription(subscriptionId: String): Flow<List<PaymentRecord>> =
        paymentRecordDao.getPaymentRecordsForSubscription(subscriptionId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getAllPaymentRecords(): Flow<List<PaymentRecord>> =
        paymentRecordDao.getAllPaymentRecords().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getPaymentRecordsBetween(
        startDate: Long,
        endDate: Long,
    ): Flow<List<PaymentRecord>> =
        paymentRecordDao.getPaymentRecordsBetween(startDate, endDate).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getTotalSpentForSubscription(subscriptionId: String): Flow<Double> =
        paymentRecordDao.getTotalSpentForSubscription(subscriptionId).map {
            it ?: 0.0
        }

    override fun getTotalSpentAllTime(): Flow<Double> = paymentRecordDao.getTotalSpentAllTime().map { it ?: 0.0 }

    override suspend fun recordPayment(paymentRecord: PaymentRecord) {
        paymentRecordDao.insertPaymentRecord(paymentRecord.toEntity())
    }

    override suspend fun deletePayment(paymentRecord: PaymentRecord) {
        paymentRecordDao.deletePaymentRecord(paymentRecord.toEntity())
    }

    override suspend fun deletePaymentRecordsForSubscription(subscriptionId: String) {
        paymentRecordDao.deletePaymentRecordsForSubscription(subscriptionId)
    }
}
