package com.wolfeleo2.subsy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.wolfeleo2.subsy.data.local.entity.CustomCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<CustomCategoryEntity>>

    @Insert
    suspend fun insert(category: CustomCategoryEntity)

    @Query("UPDATE custom_categories SET name = :name, color_hex = :colorHex WHERE id = :id")
    suspend fun update(
        id: String,
        name: String,
        colorHex: String,
    )

    @Query("UPDATE transactions SET category_key = :to WHERE category_key = :from")
    suspend fun reassignTransactions(
        from: String,
        to: String,
    )

    @Query("DELETE FROM category_budgets WHERE category_key = :key")
    suspend fun deleteBudget(key: String)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Deletes the category and its budget, and moves its transactions to [fallbackKey], so none point at nothing. */
    @Transaction
    suspend fun deleteAndReassign(
        id: String,
        key: String,
        fallbackKey: String,
    ) {
        reassignTransactions(key, fallbackKey)
        deleteBudget(key)
        deleteById(id)
    }
}
