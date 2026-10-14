package com.wolfeleo2.subsy.data.model

enum class TransactionType {
    EXPENSE,
    INCOME,
    ;

    companion object {
        fun fromString(value: String?): TransactionType = entries.firstOrNull { it.name == value } ?: EXPENSE
    }
}

/** A one-off movement of money outside the subscription ledger. [amount] is always positive; [type] gives the direction. */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Double,
    val currency: String,
    val date: Long,
    /** A [SpendCategory] name, or [CustomCategory.key] for a user-made one. */
    val categoryKey: String,
    val merchant: String? = null,
    val note: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
)

data class CustomCategory(
    val id: String,
    val name: String,
    val kind: TransactionType,
    val colorHex: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val key: String get() = CUSTOM_PREFIX + id

    companion object {
        const val CUSTOM_PREFIX = "custom:"
    }
}
