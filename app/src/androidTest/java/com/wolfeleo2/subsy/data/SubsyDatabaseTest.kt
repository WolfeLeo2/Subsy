package com.wolfeleo2.subsy.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wolfeleo2.subsy.data.local.dao.DetectedTransactionDao
import com.wolfeleo2.subsy.data.local.dao.PaymentRecordDao
import com.wolfeleo2.subsy.data.local.dao.SubscriptionDao
import com.wolfeleo2.subsy.data.local.db.SubsyDatabase
import com.wolfeleo2.subsy.data.local.entity.DetectedTransactionEntity
import com.wolfeleo2.subsy.data.local.entity.PaymentRecordEntity
import com.wolfeleo2.subsy.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SubsyDatabaseTest {
    private lateinit var database: SubsyDatabase
    private lateinit var subscriptionDao: SubscriptionDao
    private lateinit var paymentRecordDao: PaymentRecordDao
    private lateinit var detectedTransactionDao: DetectedTransactionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, SubsyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        subscriptionDao = database.subscriptionDao()
        paymentRecordDao = database.paymentRecordDao()
        detectedTransactionDao = database.detectedTransactionDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    private fun createSubscriptionEntity(
        id: String,
        name: String = "Test Service",
        status: String = "ACTIVE",
        isFreeTrial: Boolean = false,
        nextBillingDate: Long = 2000L,
    ): SubscriptionEntity =
        SubscriptionEntity(
            id = id,
            name = name,
            category = "ENTERTAINMENT",
            amount = 9.99,
            currency = "USD",
            billingCycle = "MONTHLY",
            startDate = 1000L,
            nextBillingDate = nextBillingDate,
            isFreeTrial = isFreeTrial,
            trialEndDate = if (isFreeTrial) nextBillingDate else null,
            status = status,
            cancellationUrl = "https://example.com/cancel",
            iconName = "play",
            colorHex = "#FF0000",
            notes = null,
            createdAt = 1000L,
        )

    @Test
    fun insertAndRetrieveSubscription() =
        runTest {
            val sub = createSubscriptionEntity("sub-1", "Netflix")
            subscriptionDao.insertSubscription(sub)

            val direct = subscriptionDao.getSubscriptionByIdDirect("sub-1")
            assertNotNull(direct)
            assertEquals("Netflix", direct?.name)

            val flowList = subscriptionDao.getAllSubscriptions().first()
            assertEquals(1, flowList.size)
            assertEquals("Netflix", flowList.first().name)
        }

    @Test
    fun updateSubscriptionAndStatus() =
        runTest {
            val sub = createSubscriptionEntity("sub-1", "Spotify", status = "ACTIVE")
            subscriptionDao.insertSubscription(sub)

            subscriptionDao.updateStatus("sub-1", "PAUSED")
            val updated = subscriptionDao.getSubscriptionByIdDirect("sub-1")
            assertEquals("PAUSED", updated?.status)

            subscriptionDao.updateNextBillingDate("sub-1", 5000L)
            val withNewDate = subscriptionDao.getSubscriptionByIdDirect("sub-1")
            assertEquals(5000L, withNewDate?.nextBillingDate)
        }

    @Test
    fun deleteSubscriptionCascadesToPaymentRecords() =
        runTest {
            val sub = createSubscriptionEntity("sub-1", "Gym")
            subscriptionDao.insertSubscription(sub)

            val payment =
                PaymentRecordEntity(
                    id = "pay-1",
                    subscriptionId = "sub-1",
                    amount = 9.99,
                    currency = "USD",
                    paymentDate = 1500L,
                    status = "CONFIRMED",
                )
            paymentRecordDao.insertPaymentRecord(payment)

            val paymentsBefore = paymentRecordDao.getPaymentRecordsForSubscription("sub-1").first()
            assertEquals(1, paymentsBefore.size)

            // Delete parent subscription
            subscriptionDao.deleteSubscription(sub)

            // Foreign key CASCADE should remove the payment record
            val paymentsAfter = paymentRecordDao.getPaymentRecordsForSubscription("sub-1").first()
            assertTrue(paymentsAfter.isEmpty())

            val subAfter = subscriptionDao.getSubscriptionByIdDirect("sub-1")
            assertNull(subAfter)
        }

    @Test
    fun detectedTransactionsFlowAndProcessedFlag() =
        runTest {
            val tx =
                DetectedTransactionEntity(
                    id = "tx-1",
                    merchantName = "Spotify",
                    amount = 11.99,
                    rawMessage = "Charged $11.99",
                    source = "SMS",
                    detectedAt = 1000L,
                    isProcessed = false,
                )
            detectedTransactionDao.insertTransaction(tx)

            val unprocessed = detectedTransactionDao.getUnprocessedTransactions().first()
            assertEquals(1, unprocessed.size)

            detectedTransactionDao.markAsProcessed("tx-1", true)
            val unprocessedAfter = detectedTransactionDao.getUnprocessedTransactions().first()
            assertTrue(unprocessedAfter.isEmpty())
        }

    @Test
    fun insertingDuplicateIdThrowsAndKeepsPaymentRecords() =
        runTest {
            subscriptionDao.insertSubscription(createSubscriptionEntity("sub-1", "Original"))
            paymentRecordDao.insertPaymentRecord(
                PaymentRecordEntity(
                    id = "pay-1",
                    subscriptionId = "sub-1",
                    amount = 9.99,
                    currency = "USD",
                    paymentDate = 1500L,
                    status = "CONFIRMED",
                ),
            )

            val duplicate = createSubscriptionEntity("sub-1", "Duplicate")
            val thrown = runCatching { subscriptionDao.insertSubscription(duplicate) }.exceptionOrNull()

            assertTrue(thrown is SQLiteConstraintException)
            assertEquals(1, paymentRecordDao.getPaymentRecordsForSubscription("sub-1").first().size)
        }
}
