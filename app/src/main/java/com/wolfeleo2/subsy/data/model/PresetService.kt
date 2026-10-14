package com.wolfeleo2.subsy.data.model

data class PresetService(
    val id: String,
    val name: String,
    val category: Category,
    val defaultAmount: Double,
    val defaultCurrency: String = "USD",
    val defaultBillingCycle: BillingCycle = BillingCycle.MONTHLY,
    val defaultTrialDays: Int? = null,
    val iconName: String,
    val colorHex: String,
    val cancellationUrl: String,
    val keywords: List<String> = emptyList(),
    val description: String? = null,
)
