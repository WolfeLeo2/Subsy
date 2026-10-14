package com.wolfeleo2.subsy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A monthly limit for one spend category, in the currency it was set in. */
@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey
    @ColumnInfo(name = "category_key")
    val categoryKey: String,
    @ColumnInfo(name = "amount")
    val amount: Double,
    /** Empty only for rows saved before v4, until [CategoryBudgetDao.pinUnsetCurrency] fills it in. */
    @ColumnInfo(name = "currency", defaultValue = "")
    val currency: String,
)
