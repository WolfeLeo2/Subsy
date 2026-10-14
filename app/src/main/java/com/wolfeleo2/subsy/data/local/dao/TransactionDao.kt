package com.wolfeleo2.subsy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wolfeleo2.subsy.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, created_at DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    /** Merchant memory: the category of the latest transaction of this kind with the same merchant. */
    @Query(
        "SELECT category_key FROM transactions WHERE merchant = :merchant COLLATE NOCASE AND type = :type " +
            "ORDER BY date DESC, created_at DESC LIMIT 1",
    )
    suspend fun latestCategoryFor(
        merchant: String,
        type: String,
    ): String?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)
}
