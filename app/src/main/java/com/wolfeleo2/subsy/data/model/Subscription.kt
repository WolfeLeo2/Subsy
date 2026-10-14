package com.wolfeleo2.subsy.data.model

data class Subscription(
    val id: String,
    val name: String,
    val category: Category,
    val amount: Double,
    val currency: String = "USD",
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    /** Billing anchor: the first charge. Later charges are counted in whole cycles from it. */
    val startDate: Long,
    val nextBillingDate: Long,
    /** Signed up with a free trial. Whether it is trialing right now is `status == TRIAL`. */
    val isFreeTrial: Boolean = false,
    val trialEndDate: Long? = null,
    val status: SubscriptionStatus = if (isFreeTrial) SubscriptionStatus.TRIAL else SubscriptionStatus.ACTIVE,
    val cancellationUrl: String? = null,
    val iconName: String? = null,
    val colorHex: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Normalized monthly cost of this plan, whatever its status. */
    val monthlyCost: Double
        get() = billingCycle.normalizedMonthlyAmount(amount)

    /** Running or trialing: the plans that count toward totals. */
    val isLive: Boolean
        get() = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL

    /** Only ACTIVE plans count toward the burn rate; trials cost nothing until they convert. */
    val monthlyBurnRate: Double
        get() = if (status == SubscriptionStatus.ACTIVE) monthlyCost else 0.0
}
