package com.wolfeleo2.subsy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wolfeleo2.subsy.data.local.entity.CategoryBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets")
    fun getAll(): Flow<List<CategoryBudgetEntity>>

    @Upsert
    suspend fun upsert(budget: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets WHERE category_key = :key")
    suspend fun delete(key: String)

    @Query("UPDATE category_budgets SET currency = :currency WHERE currency = ''")
    suspend fun pinUnsetCurrency(currency: String)
}
