package com.wolfeleo2.subsy.ui.util

import androidx.compose.ui.graphics.Color

/** Parses "#RRGGBB" or "#AARRGGBB"; null when blank or malformed. */
fun parseColorHex(hex: String?): Color? {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    val value = clean.toLongOrNull(16) ?: return null
    return when (clean.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}
