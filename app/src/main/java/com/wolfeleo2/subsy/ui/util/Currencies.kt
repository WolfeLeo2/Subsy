package com.wolfeleo2.subsy.ui.util

/** Currencies offered in pickers, in the order they're listed. */
val COMMON_CURRENCIES =
    listOf("USD", "EUR", "GBP", "NGN", "JPY", "CAD", "AUD", "INR", "CHF", "CNY", "BRL", "MXN", "ZAR", "KES", "SEK", "GHS")

/** [COMMON_CURRENCIES] plus any of [include] it lacks, so a stored code is always selectable. */
fun currencyChoices(vararg include: String): List<String> = COMMON_CURRENCIES + include.filterNot { it in COMMON_CURRENCIES }.distinct()
