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
        Index(value = ["reference"]),
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
    // v5: what the on-device model read. Rows from before v5 never existed in practice (nothing wrote this table).
    @ColumnInfo(name = "currency", defaultValue = "")
    val currency: String = "",
    @ColumnInfo(name = "kind", defaultValue = "EXPENSE")
    val kind: String = "EXPENSE",
    @ColumnInfo(name = "reference")
    val reference: String? = null,
    @ColumnInfo(name = "category_key")
    val categoryKey: String? = null,
    /** SMS sender address or the notifying app's package. */
    @ColumnInfo(name = "sender")
    val sender: String? = null,
)
