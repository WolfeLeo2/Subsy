package com.wolfeleo2.subsy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.wolfeleo2.subsy.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    /** Everything the user has, without archived (deleted but with payment history) ones. */
    @Query("SELECT * FROM subscriptions WHERE archived_at IS NULL ORDER BY next_billing_date ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    /** Archived ones too: for labelling and categorising past payments. */
    @Query("SELECT * FROM subscriptions ORDER BY next_billing_date ASC")
    fun getSubscriptionHistory(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM payment_records WHERE subscription_id = :id)")
    suspend fun hasPayments(id: String): Boolean

    @Query("UPDATE subscriptions SET archived_at = :at, status = 'CANCELLED' WHERE id = :id")
    suspend fun archive(
        id: String,
        at: Long,
    )

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    fun getSubscriptionById(id: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getSubscriptionByIdDirect(id: String): SubscriptionEntity?

    // ABORT, not REPLACE: REPLACE deletes the row first and cascades to its payment records.
    @Insert
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionById(id: String)

    @Query("UPDATE subscriptions SET next_billing_date = :nextBillingDate WHERE id = :id")
    suspend fun updateNextBillingDate(
        id: String,
        nextBillingDate: Long,
    )

    @Query("UPDATE subscriptions SET status = :status WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
    )
}
