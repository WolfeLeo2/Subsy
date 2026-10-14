package com.wolfeleo2.subsy.data.repository

import com.wolfeleo2.subsy.data.local.dao.CategoryBudgetDao
import com.wolfeleo2.subsy.data.local.entity.CategoryBudgetEntity
import com.wolfeleo2.subsy.data.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Per-category monthly budgets, keyed like transactions' category keys. The overall budget lives in settings. */
interface BudgetRepository {
    fun getCategoryBudgets(): Flow<Map<String, Money>>

    /** Sets the budget for [key]; null removes it. */
    suspend fun setCategoryBudget(
        key: String,
        budget: Money?,
    )

    /** Gives budgets saved before they carried a currency the [currency] they were entered in. */
    suspend fun pinUnsetCurrency(currency: String)
}

class DefaultBudgetRepository(
    private val dao: CategoryBudgetDao,
) : BudgetRepository {
    override fun getCategoryBudgets(): Flow<Map<String, Money>> =
        dao.getAll().map { list -> list.associate { it.categoryKey to Money(it.amount, it.currency) } }

    override suspend fun setCategoryBudget(
        key: String,
        budget: Money?,
    ) = if (budget == null) dao.delete(key) else dao.upsert(CategoryBudgetEntity(key, budget.amount, budget.currency))

    override suspend fun pinUnsetCurrency(currency: String) = dao.pinUnsetCurrency(currency)
}
