package com.wolfeleo2.subsy.data.model

data class PaymentRecord(
    val id: String,
    val subscriptionId: String,
    val amount: Double,
    val currency: String = "USD",
    val paymentDate: Long = System.currentTimeMillis(),
    val status: PaymentStatus = PaymentStatus.AUTO_LOGGED,
)
