package com.wolfeleo2.subsy

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.wolfeleo2.subsy.data.local.db.SubsyDatabase
import com.wolfeleo2.subsy.data.repository.BudgetRepository
import com.wolfeleo2.subsy.data.repository.CategoryRepository
import com.wolfeleo2.subsy.data.repository.DefaultBudgetRepository
import com.wolfeleo2.subsy.data.repository.DefaultCategoryRepository
import com.wolfeleo2.subsy.data.repository.DefaultPaymentRecordRepository
import com.wolfeleo2.subsy.data.repository.DefaultSubscriptionRepository
import com.wolfeleo2.subsy.data.repository.DefaultTransactionDetectorRepository
import com.wolfeleo2.subsy.data.repository.DefaultTransactionRepository
import com.wolfeleo2.subsy.data.repository.ExchangeRateRepository
import com.wolfeleo2.subsy.data.repository.PaymentRecordRepository
import com.wolfeleo2.subsy.data.repository.SettingsRepository
import com.wolfeleo2.subsy.data.repository.SubscriptionRepository
import com.wolfeleo2.subsy.data.repository.TransactionDetectorRepository
import com.wolfeleo2.subsy.data.repository.TransactionRepository
import com.wolfeleo2.subsy.detection.ModelRepository
import com.wolfeleo2.subsy.worker.DailyWorker

class SubsyApplication :
    Application(),
    SingletonImageLoader.Factory {
    // Manual DI: one instance per process, reachable from screens, workers, receivers and tiles.
    private val database by lazy { SubsyDatabase.getDatabase(this) }
    val subscriptionRepository: SubscriptionRepository by lazy { DefaultSubscriptionRepository(database.subscriptionDao()) }
    val paymentRecordRepository: PaymentRecordRepository by lazy { DefaultPaymentRecordRepository(database.paymentRecordDao()) }
    val transactionRepository: TransactionRepository by lazy { DefaultTransactionRepository(database.transactionDao()) }
    val categoryRepository: CategoryRepository by lazy { DefaultCategoryRepository(database.customCategoryDao()) }
    val budgetRepository: BudgetRepository by lazy { DefaultBudgetRepository(database.categoryBudgetDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val exchangeRateRepository by lazy { ExchangeRateRepository(filesDir.resolve("exchange_rates.json")) }
    val modelRepository by lazy { ModelRepository(this) }
    val detectorRepository: TransactionDetectorRepository by lazy {
        DefaultTransactionDetectorRepository(database.detectedTransactionDao(), database.paymentRecordDao())
    }

    override fun onCreate() {
        super.onCreate()
        DailyWorker.schedule(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }.crossfade(true)
            .build()
}
