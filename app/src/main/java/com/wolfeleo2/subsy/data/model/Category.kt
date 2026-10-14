package com.wolfeleo2.subsy.data.model

enum class Category(
    val displayName: String,
    val defaultIconName: String,
    val defaultColorHex: String,
) {
    ENTERTAINMENT(
        displayName = "Entertainment",
        defaultIconName = "play_circle",
        defaultColorHex = "#8B5CF6", // Purple
    ),
    WORK_SAAS(
        displayName = "Work & SaaS",
        defaultIconName = "laptop",
        defaultColorHex = "#3B82F6", // Blue
    ),
    HEALTH_FITNESS(
        displayName = "Health & Fitness",
        defaultIconName = "fitness_center",
        defaultColorHex = "#10B981", // Green
    ),
    UTILITIES(
        displayName = "Utilities & Security",
        defaultIconName = "shield",
        defaultColorHex = "#0891B2", // Cyan
    ),
    PERSONAL(
        displayName = "Personal & Lifestyle",
        defaultIconName = "person",
        defaultColorHex = "#DB2777", // Pink
    ),
    OTHER(
        displayName = "Other",
        defaultIconName = "credit_card",
        defaultColorHex = "#64748B", // Slate
    ),
    ;

    companion object {
        fun fromString(value: String?): Category {
            if (value.isNullOrBlank()) return OTHER
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}
