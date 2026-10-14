package com.wolfeleo2.subsy.data.model

data class DetectedTransaction(
    val id: String,
    val merchantName: String,
    val amount: Double,
    val rawMessage: String,
    val source: TransactionSource,
    val detectedAt: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false,
)
