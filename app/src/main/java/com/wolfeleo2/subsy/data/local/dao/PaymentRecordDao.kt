package com.wolfeleo2.subsy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wolfeleo2.subsy.data.local.entity.PaymentRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentRecordDao {
    @Query("SELECT * FROM payment_records WHERE subscription_id = :subscriptionId ORDER BY payment_date DESC")
    fun getPaymentRecordsForSubscription(subscriptionId: String): Flow<List<PaymentRecordEntity>>

    @Query("SELECT * FROM payment_records ORDER BY payment_date DESC")
    fun getAllPaymentRecords(): Flow<List<PaymentRecordEntity>>

    @Query(
        """
        SELECT * FROM payment_records 
        WHERE payment_date BETWEEN :startDate AND :endDate 
        ORDER BY payment_date DESC
        """,
    )
    fun getPaymentRecordsBetween(
        startDate: Long,
        endDate: Long,
    ): Flow<List<PaymentRecordEntity>>

    @Query("SELECT SUM(amount) FROM payment_records WHERE subscription_id = :subscriptionId")
    fun getTotalSpentForSubscription(subscriptionId: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM payment_records")
    fun getTotalSpentAllTime(): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentRecord(record: PaymentRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentRecords(records: List<PaymentRecordEntity>)

    @Delete
    suspend fun deletePaymentRecord(record: PaymentRecordEntity)

    @Query("DELETE FROM payment_records WHERE subscription_id = :subscriptionId")
    suspend fun deletePaymentRecordsForSubscription(subscriptionId: String)
}
