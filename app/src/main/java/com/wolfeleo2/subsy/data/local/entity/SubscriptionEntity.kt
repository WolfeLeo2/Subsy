package com.wolfeleo2.subsy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "amount")
    val amount: Double,
    @ColumnInfo(name = "currency")
    val currency: String,
    @ColumnInfo(name = "billing_cycle")
    val billingCycle: String,
    @ColumnInfo(name = "start_date")
    val startDate: Long,
    @ColumnInfo(name = "next_billing_date")
    val nextBillingDate: Long,
    @ColumnInfo(name = "is_free_trial")
    val isFreeTrial: Boolean,
    @ColumnInfo(name = "trial_end_date")
    val trialEndDate: Long?,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "cancellation_url")
    val cancellationUrl: String?,
    @ColumnInfo(name = "icon_name")
    val iconName: String?,
    @ColumnInfo(name = "color_hex")
    val colorHex: String?,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    // v6: where payments count, the name banks print, and archiving instead of deleting payment history.
    @ColumnInfo(name = "spend_category")
    val spendCategory: String? = null,
    @ColumnInfo(name = "bank_name")
    val bankName: String? = null,
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long? = null,
)
