package com.wolfeleo2.subsy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.wolfeleo2.subsy.data.local.entity.DetectedTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedTransactionDao {
    @Query("SELECT * FROM detected_transactions WHERE is_processed = 0 ORDER BY detected_at DESC")
    fun getUnprocessedTransactions(): Flow<List<DetectedTransactionEntity>>

    @Query("SELECT * FROM detected_transactions ORDER BY detected_at DESC")
    fun getAllTransactions(): Flow<List<DetectedTransactionEntity>>

    @Query("SELECT COUNT(*) FROM detected_transactions")
    fun countAll(): Flow<Int>

    @Query("SELECT * FROM detected_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): DetectedTransactionEntity?

    @Insert
    suspend fun insertTransaction(transaction: DetectedTransactionEntity)

    /** A charge already stored under this reference (an SMS and a bank notification, or a repeated SMS). */
    @Query("SELECT EXISTS(SELECT 1 FROM detected_transactions WHERE reference = :reference)")
    suspend fun hasReference(reference: String): Boolean

    /** The same amount and currency seen in [from, to], for messages without a reference. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM detected_transactions WHERE amount = :amount AND currency = :currency " +
            "AND detected_at BETWEEN :from AND :to)",
    )
    suspend fun hasSimilar(
        amount: Double,
        currency: String,
        from: Long,
        to: Long,
    ): Boolean

    @Query("UPDATE detected_transactions SET is_processed = :isProcessed WHERE id = :id")
    suspend fun markAsProcessed(
        id: String,
        isProcessed: Boolean = true,
    )

    @Delete
    suspend fun deleteTransaction(transaction: DetectedTransactionEntity)

    @Query("DELETE FROM detected_transactions WHERE is_processed = 1")
    suspend fun clearProcessedTransactions()
}
