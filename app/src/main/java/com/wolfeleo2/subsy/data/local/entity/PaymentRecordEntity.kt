package com.wolfeleo2.subsy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_records",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subscription_id"]),
        Index(value = ["payment_date"]),
    ],
)
data class PaymentRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: String,
    @ColumnInfo(name = "amount")
    val amount: Double,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "payment_date")
    val paymentDate: Long,
    @ColumnInfo(name = "status")
    val status: String,
)
