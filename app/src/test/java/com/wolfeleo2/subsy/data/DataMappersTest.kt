package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.local.entity.DetectedTransactionEntity
import com.wolfeleo2.subsy.data.local.entity.PaymentRecordEntity
import com.wolfeleo2.subsy.data.mapper.toDomain
import com.wolfeleo2.subsy.data.mapper.toEntity
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.model.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class DataMappersTest {
    @Test
    fun subscriptionEntityAndDomainMapping() {
        val domain =
            Subscription(
                id = "sub-123",
                name = "Spotify",
                category = Category.ENTERTAINMENT,
                amount = 11.99,
                currency = "USD",
                billingCycle = BillingCycle.MONTHLY,
                startDate = 1000L,
                nextBillingDate = 2000L,
                isFreeTrial = false,
                trialEndDate = null,
                status = SubscriptionStatus.ACTIVE,
                cancellationUrl = "https://spotify.com/cancel",
                iconName = "spotify",
                colorHex = "#1DB954",
                notes = "Family plan",
                createdAt = 500L,
            )

        val entity = domain.toEntity()
        assertEquals("sub-123", entity.id)
        assertEquals("Spotify", entity.name)
        assertEquals("ENTERTAINMENT", entity.category)
        assertEquals("MONTHLY", entity.billingCycle)
        assertEquals(11.99, entity.amount, 0.001)

        val mappedBack = entity.toDomain()
        assertEquals(domain, mappedBack)
    }

    @Test
    fun paymentRecordEntityAndDomainMapping() {
        val entity =
            PaymentRecordEntity(
                id = "pay-456",
                subscriptionId = "sub-123",
                amount = 11.99,
                currency = "USD",
                paymentDate = 1500L,
                status = "AUTO_LOGGED",
            )

        val domain = entity.toDomain()
        assertEquals("pay-456", domain.id)
        assertEquals("sub-123", domain.subscriptionId)
        assertEquals(PaymentStatus.AUTO_LOGGED, domain.status)

        val mappedBack = domain.toEntity()
        assertEquals(entity, mappedBack)
    }

    @Test
    fun detectedTransactionEntityAndDomainMapping() {
        val entity =
            DetectedTransactionEntity(
                id = "tx-789",
                merchantName = "Netflix",
                amount = 15.49,
                rawMessage = "Your card was debited $15.49 at Netflix",
                source = "SMS",
                detectedAt = 2500L,
                isProcessed = false,
            )

        val domain = entity.toDomain()
        assertEquals(TransactionSource.SMS, domain.source)
        assertEquals("Netflix", domain.merchantName)

        val mappedBack = domain.toEntity()
        assertEquals(entity, mappedBack)
    }
}
