package com.wolfeleo2.subsy.data.model

/** Where a money movement came from. Detected transactions use SMS or NOTIFICATION only. */
enum class TransactionSource {
    MANUAL,
    SMS,
    NOTIFICATION,
    ;

    companion object {
        fun fromString(value: String?): TransactionSource {
            if (value.isNullOrBlank()) return NOTIFICATION
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NOTIFICATION
        }
    }
}
