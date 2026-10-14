package com.wolfeleo2.subsy.data.local.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wolfeleo2.subsy.data.local.dao.CategoryBudgetDao
import com.wolfeleo2.subsy.data.local.dao.CustomCategoryDao
import com.wolfeleo2.subsy.data.local.dao.DetectedTransactionDao
import com.wolfeleo2.subsy.data.local.dao.PaymentRecordDao
import com.wolfeleo2.subsy.data.local.dao.SubscriptionDao
import com.wolfeleo2.subsy.data.local.dao.TransactionDao
import com.wolfeleo2.subsy.data.local.entity.CategoryBudgetEntity
import com.wolfeleo2.subsy.data.local.entity.CustomCategoryEntity
import com.wolfeleo2.subsy.data.local.entity.DetectedTransactionEntity
import com.wolfeleo2.subsy.data.local.entity.PaymentRecordEntity
import com.wolfeleo2.subsy.data.local.entity.SubscriptionEntity
import com.wolfeleo2.subsy.data.local.entity.TransactionEntity

@Database(
    entities = [
        SubscriptionEntity::class,
        PaymentRecordEntity::class,
        DetectedTransactionEntity::class,
        TransactionEntity::class,
        CustomCategoryEntity::class,
        CategoryBudgetEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Every version change needs an entry here (or a Migration): user data must survive upgrades.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class SubsyDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun paymentRecordDao(): PaymentRecordDao

    abstract fun detectedTransactionDao(): DetectedTransactionDao

    abstract fun transactionDao(): TransactionDao

    abstract fun customCategoryDao(): CustomCategoryDao

    abstract fun categoryBudgetDao(): CategoryBudgetDao

    companion object {
        @Volatile
        private var instance: SubsyDatabase? = null

        fun getDatabase(context: Context): SubsyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, SubsyDatabase::class.java, DATABASE_NAME)
                    .build()
                    .also { instance = it }
            }

        const val DATABASE_NAME = "subsy.db"
    }
}
