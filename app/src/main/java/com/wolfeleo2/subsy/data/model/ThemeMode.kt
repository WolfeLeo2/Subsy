package com.wolfeleo2.subsy.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromString(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
