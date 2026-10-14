package com.wolfeleo2.subsy.data.model

/** Which way money moved in a detected message. TRANSFER is between the user's own accounts. */
enum class DetectedKind {
    EXPENSE,
    INCOME,
    TRANSFER,
    ;

    companion object {
        fun fromString(value: String?): DetectedKind = entries.firstOrNull { it.name == value } ?: EXPENSE
    }
}

data class DetectedTransaction(
    val id: String,
    val merchantName: String,
    val amount: Double,
    val rawMessage: String,
    val source: TransactionSource,
    val detectedAt: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false,
    /** ISO 4217 code; empty only for rows from before the model existed. */
    val currency: String = "",
    val kind: DetectedKind = DetectedKind.EXPENSE,
    /** The transaction reference from the message, used to drop the same charge reported twice. */
    val reference: String? = null,
    /** The model's [SpendCategory] guess, a first suggestion only. */
    val categoryKey: String? = null,
    val sender: String? = null,
)
