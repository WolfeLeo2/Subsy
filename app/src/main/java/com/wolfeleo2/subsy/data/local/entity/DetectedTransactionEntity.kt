package com.wolfeleo2.subsy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detected_transactions",
    indices = [
        Index(value = ["is_processed"]),
        Index(value = ["detected_at"]),
    ],
)
data class DetectedTransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    @ColumnInfo(name = "amount")
    val amount: Double,
    @ColumnInfo(name = "raw_message")
    val rawMessage: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "detected_at")
    val detectedAt: Long,
    @ColumnInfo(name = "is_processed")
    val isProcessed: Boolean,
)
