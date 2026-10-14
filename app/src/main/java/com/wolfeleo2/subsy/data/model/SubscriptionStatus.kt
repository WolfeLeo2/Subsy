package com.wolfeleo2.subsy.data.model

enum class SubscriptionStatus {
    ACTIVE,
    TRIAL,
    PAUSED,
    CANCELLED,
    ;

    companion object {
        fun fromString(value: String?): SubscriptionStatus {
            if (value.isNullOrBlank()) return ACTIVE
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
        }
    }
}
