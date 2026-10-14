package com.wolfeleo2.subsy.data.mapper

import com.wolfeleo2.subsy.data.local.entity.CustomCategoryEntity
import com.wolfeleo2.subsy.data.local.entity.DetectedTransactionEntity
import com.wolfeleo2.subsy.data.local.entity.PaymentRecordEntity
import com.wolfeleo2.subsy.data.local.entity.SubscriptionEntity
import com.wolfeleo2.subsy.data.local.entity.TransactionEntity
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.DetectedTransaction
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionSource
import com.wolfeleo2.subsy.data.model.TransactionType

fun SubscriptionEntity.toDomain(): Subscription =
    Subscription(
        id = id,
        name = name,
        category = Category.fromString(category),
        amount = amount,
        currency = currency,
        billingCycle = BillingCycle.fromSerialized(billingCycle),
        startDate = startDate,
        nextBillingDate = nextBillingDate,
        isFreeTrial = isFreeTrial,
        trialEndDate = trialEndDate,
        status = SubscriptionStatus.fromString(status),
        cancellationUrl = cancellationUrl,
        iconName = iconName,
        colorHex = colorHex,
        notes = notes,
        createdAt = createdAt,
    )

fun Subscription.toEntity(): SubscriptionEntity =
    SubscriptionEntity(
        id = id,
        name = name,
        category = category.name,
        amount = amount,
        currency = currency,
        billingCycle = billingCycle.serializedName,
        startDate = startDate,
        nextBillingDate = nextBillingDate,
        isFreeTrial = isFreeTrial,
        trialEndDate = trialEndDate,
        status = status.name,
        cancellationUrl = cancellationUrl,
        iconName = iconName,
        colorHex = colorHex,
        notes = notes,
        createdAt = createdAt,
    )

fun PaymentRecordEntity.toDomain(): PaymentRecord =
    PaymentRecord(
        id = id,
        subscriptionId = subscriptionId,
        amount = amount,
        currency = currency,
        paymentDate = paymentDate,
        status = PaymentStatus.fromString(status),
    )

fun PaymentRecord.toEntity(): PaymentRecordEntity =
    PaymentRecordEntity(
        id = id,
        subscriptionId = subscriptionId,
        amount = amount,
        currency = currency,
        paymentDate = paymentDate,
        status = status.name,
    )

fun DetectedTransactionEntity.toDomain(): DetectedTransaction =
    DetectedTransaction(
        id = id,
        merchantName = merchantName,
        amount = amount,
        rawMessage = rawMessage,
        source = TransactionSource.fromString(source),
        detectedAt = detectedAt,
        isProcessed = isProcessed,
    )

fun DetectedTransaction.toEntity(): DetectedTransactionEntity =
    DetectedTransactionEntity(
        id = id,
        merchantName = merchantName,
        amount = amount,
        rawMessage = rawMessage,
        source = source.name,
        detectedAt = detectedAt,
        isProcessed = isProcessed,
    )

fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        id = id,
        type = TransactionType.fromString(type),
        amount = amount,
        currency = currency,
        date = date,
        categoryKey = categoryKey,
        merchant = merchant,
        note = note,
        source = TransactionSource.fromString(source),
        createdAt = createdAt,
    )

fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
        id = id,
        type = type.name,
        amount = amount,
        currency = currency,
        date = date,
        categoryKey = categoryKey,
        merchant = merchant,
        note = note,
        source = source.name,
        createdAt = createdAt,
    )

fun CustomCategoryEntity.toDomain(): CustomCategory =
    CustomCategory(id = id, name = name, kind = TransactionType.fromString(kind), colorHex = colorHex, createdAt = createdAt)

fun CustomCategory.toEntity(): CustomCategoryEntity =
    CustomCategoryEntity(id = id, name = name, kind = kind.name, colorHex = colorHex, createdAt = createdAt)
