package org.totschnig.myexpenses.contrib

import org.totschnig.myexpenses.util.licence.AddOnPackage

/**
 * Configuration for purchase.
 */
object Config {
    // SKUs for our products:
    const val SKU_PREMIUM = "sku_premium"
    const val SKU_EXTENDED = "sku_extended"
    const val SKU_PREMIUM2EXTENDED = "sku_premium2extended"

    /**
     * only used on Amazon
     */
    const val SKU_PROFESSIONAL_PARENT = "sku_professional"
    const val SKU_PROFESSIONAL_1 = "sku_professional_monthly"
    const val SKU_PROFESSIONAL_12 = "sku_professional_yearly"

    /**
     * only used on Amazon
     */
    const val SKU_EXTENDED2PROFESSIONAL_PARENT = "sku_extended2professional"

    /**
     * only used on Amazon
     */
    const val SKU_EXTENDED2PROFESSIONAL_1 = "sku_extended2professional_monthly"
    const val SKU_EXTENDED2PROFESSIONAL_12 = "sku_extended2professional_yearly"

    val amazonSkus = listOf(SKU_PREMIUM, SKU_EXTENDED, SKU_PREMIUM2EXTENDED, SKU_PROFESSIONAL_1, SKU_PROFESSIONAL_12, SKU_EXTENDED2PROFESSIONAL_1, SKU_EXTENDED2PROFESSIONAL_12)
    val playInAppSkus = listOf(SKU_PREMIUM, SKU_EXTENDED, SKU_PREMIUM2EXTENDED, *AddOnPackage.values.map { it.sku }.toTypedArray())
    val playSubsSkus = listOf(SKU_PROFESSIONAL_1, SKU_PROFESSIONAL_12)
}