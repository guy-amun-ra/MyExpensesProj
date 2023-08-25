package org.totschnig.myexpenses.util.licence

import android.app.Application
import androidx.annotation.VisibleForTesting
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.activity.IapActivity
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import timber.log.Timber
import java.util.*

open class PlayStoreLicenceHandler(
    context: Application,
    preferenceObfuscator: PreferenceObfuscator,
    crashHandler: CrashHandler,
    prefHandler: PrefHandler,
    repository: Repository,
    currencyFormatter: CurrencyFormatter
) : AbstractInAppPurchaseLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler, repository, currencyFormatter) {
    private fun storeSkuDetails(inventory: List<ProductDetails>) {
        val editor = pricesPrefs.edit()
        for (productDetails in inventory) {
            val price: String? = if (productDetails.productType == ProductType.INAPP) {
                productDetails.oneTimePurchaseOfferDetails?.formattedPrice
            } else { //SUBS
                productDetails.subscriptionOfferDetails?.get(0)?.pricingPhases?.pricingPhaseList?.get(0)?.formattedPrice
            }
            if (price != null) {
                editor.putString(prefKeyForSkuPrice(productDetails.productId), price)
            } else {
                CrashHandler.report(
                    Exception("Unable to query price for ${productDetails.productId}")
                )
            }
        }
        editor.apply()
    }

    private fun prefKeyForSkuPrice(sku: String): String {
        return String.format(Locale.ROOT, "%s_price", sku)
    }

    override fun getDisplayPriceForPackage(aPackage: Package) =
        getPriceFromPrefs(getSkuForPackage(aPackage))

    @Suppress("MemberVisibilityCanBePrivate")
    fun getPriceFromPrefs(sku: String) =
        pricesPrefs.getString(prefKeyForSkuPrice(sku), null)

    /**
     * Pair of sku and purchaseToken
     */
    private val currentSubscription: Pair<String, String>?
        get() {
            val sku = licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION_SKU, null)
            val purchaseToken =
                licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION_PURCHASE_TOKEN, null)
            return if (sku != null && purchaseToken != null) Pair(sku, purchaseToken) else null
        }

    override fun initBillingManager(activity: IapActivity, query: Boolean): BillingManager {
        val billingUpdatesListener: BillingUpdatesListener = object : BillingUpdatesListener {
            override fun onPurchasesUpdated(
                purchases: List<Purchase>?,
                newPurchase: Boolean
            ): Boolean {
                if (purchases != null) {
                    val oldStatus = licenceStatus
                    val oldFeatures = addOnFeatures.size
                    registerInventory(purchases, newPurchase)

                    if (newPurchase || oldStatus != licenceStatus || addOnFeatures.size > oldFeatures) {
                        activity.onLicenceStatusSet(prettyPrintStatus(activity))
                    }
                }
                return licenceStatus != null || addOnFeatures.isNotEmpty()
            }

            override fun onPurchaseCanceled() {
                log().i("onPurchasesUpdated() - user cancelled the purchase flow - skipping")
            }

            override fun onPurchaseFailed(resultCode: Int) {
                Timber.e("onPurchasesUpdated() got unknown resultCode: %s", resultCode)
                activity.onPurchaseFailed(resultCode)
            }
        }
        val skuDetailsResponseListener =
            if (query) ProductDetailsResponseListener { result: BillingResult, productDetails: List<ProductDetails> ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    storeSkuDetails(productDetails)
                } else {
                    log().w("skuDetails response %d", result.responseCode)
                }
            } else null
        return BillingManagerPlay(activity, billingUpdatesListener, skuDetailsResponseListener)
    }

    @VisibleForTesting
    fun findHighestValidPurchase(inventory: List<Purchase>) = inventory.mapNotNull { purchase ->
        extractLicenceStatusFromSku(purchase.products[0])?.let {
            Pair(purchase, it)
        }
    }.maxByOrNull { pair -> pair.second }?.first

    private fun registerInventory(inventory: List<Purchase>, newPurchase: Boolean) {
        inventory.forEach { purchase: Purchase ->
            log().i(
                "%s (acknowledged %b)",
                purchase.products.joinToString(),
                purchase.isAcknowledged
            )
        }
        findHighestValidPurchase(inventory)?.also {
            if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                handlePurchaseForLicence(it.products[0], it.orderId!!, it.purchaseToken)
            } else {
                //TODO handle pending
                CrashHandler.report(
                    Exception("Found purchase in state ${it.purchaseState}"),
                    TAG
                )
            }
        } ?: run {
            if (!newPurchase) {
                maybeCancel()
            }
        }
        handlePurchaseForAddOns(
            inventory.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap { it.products }.mapNotNull { Licence.parseFeature(it) },
            newPurchase
        )
        licenseStatusPrefs.commit()
    }

    override suspend fun launchPurchase(
        aPackage: Package,
        shouldReplaceExisting: Boolean,
        billingManager: BillingManager
    ) {
        val sku = getSkuForPackage(aPackage)
        val oldPurchase = if (shouldReplaceExisting) {
            currentSubscription.also {
                checkNotNull(it) { "Could not determine current subscription" }
                check(sku != it.first)
            }!!.second
        } else {
            null
        }
        val type = if (aPackage is ProfessionalPackage) ProductType.SUBS else ProductType.INAPP
        (billingManager as BillingManagerPlay).initiatePurchaseFlow(sku, type, oldPurchase)
    }
}