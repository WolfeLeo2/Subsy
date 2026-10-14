package com.wolfeleo2.subsy.data.model

enum class PaymentStatus {
    CONFIRMED,
    AUTO_LOGGED,
    MANUALLY_LOGGED,
    ;

    companion object {
        fun fromString(value: String?): PaymentStatus {
            if (value.isNullOrBlank()) return MANUALLY_LOGGED
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MANUALLY_LOGGED
        }
    }
}
