package com.wolfeleo2.subsy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["date"]),
        Index(value = ["category_key"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "amount")
    val amount: Double,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "date")
    val date: Long,
    @ColumnInfo(name = "category_key")
    val categoryKey: String,
    @ColumnInfo(name = "merchant")
    val merchant: String?,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
