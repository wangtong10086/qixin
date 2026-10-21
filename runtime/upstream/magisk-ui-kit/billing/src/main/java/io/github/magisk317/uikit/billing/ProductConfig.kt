package io.github.magisk317.uikit.billing

object ProductConfig {
    // In-app product IDs for donations (the only Play products; there is no
    // subscription tier in any consumer app).
    val DONATION_IDS = listOf(
        "donate_099",   // $0.99
        "donate_200",   // $2.00
        "donate_999",   // $9.99
        "donate_1999",  // $19.99
    )

    // Donations are intentionally repeatable. Never infer consumption from INAPP alone: any
    // future entitlement remains non-consumable until it is explicitly added here.
    private val CONSUMABLE_PRODUCT_IDS = DONATION_IDS.toSet()

    internal val PURCHASE_POLICY = BillingProductPolicy(
        subscriptionProductIds = emptySet(),
        consumableProductIds = CONSUMABLE_PRODUCT_IDS,
    )
}
