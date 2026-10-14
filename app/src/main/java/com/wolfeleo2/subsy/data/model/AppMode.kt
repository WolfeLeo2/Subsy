package com.wolfeleo2.subsy.data.model

/** What the user tracks. Switching never deletes data; SUBSCRIPTIONS only hides the transaction screens. */
enum class AppMode {
    SUBSCRIPTIONS,
    ALL_SPENDING,
    ;

    companion object {
        fun fromString(value: String?): AppMode? = entries.firstOrNull { it.name == value }
    }
}
