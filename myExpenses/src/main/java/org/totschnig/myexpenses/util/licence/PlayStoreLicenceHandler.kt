package org.totschnig.myexpenses.util.licence

import android.app.Activity
import android.app.Application
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsResponseListener
import com.google.android.vending.licensing.PreferenceObfuscator
import org.json.JSONException
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import java.util.*

open class PlayStoreLicenceHandler(context: Application, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler, prefHandler: PrefHandler) : AbstractInAppPurchaseLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler) {
    private fun storeSkuDetails(inventory: List<SkuDetails>) {
        val editor = pricesPrefs.edit()
        for (skuDetails in inventory) {
            editor.putString(prefKeyForSkuJson(skuDetails.sku), skuDetails.originalJson)
        }
        editor.apply()
    }

    private fun prefKeyForSkuJson(sku: String): String {
        return String.format(Locale.ROOT, "%s_json", sku)
    }

    override fun getDisplayPriceForPackage(aPackage: Package) =
            getSkuDetailsFromPrefs(getSkuForPackage(aPackage))?.let { skuDetails ->
                skuDetails.introductoryPrice.takeIf { !TextUtils.isEmpty(it) } ?: skuDetails.price
            }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getSkuDetailsFromPrefs(sku: String): SkuDetails? {
        pricesPrefs.getString(prefKeyForSkuJson(sku), null)?.let {
            return try {
                return SkuDetails(it)
            } catch (e: JSONException) {
                CrashHandler.report(e, String.format("unable to parse %s", it))
                null
            }
        } ?: run {
            CrashHandler.report(String.format("originalJson not found for %s", sku))
        }
        return null
    }

    /**
     * Pair of sku and purchaseToken
     */
    private val currentSubscription: Pair<String, String>?
        get() {
            val sku = licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION_SKU, null)
            val purchaseToken = licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION_PURCHASE_TOKEN, null)
            return if (sku != null && purchaseToken != null) Pair(sku, purchaseToken) else null
        }

    override fun initBillingManager(activity: Activity, query: Boolean): BillingManager {
        val billingUpdatesListener: BillingUpdatesListener = object : BillingUpdatesListener {
            override fun onPurchasesUpdated(purchases: List<Purchase>?, newPurchase: Boolean): Boolean {
                if (purchases != null) {
                    val oldStatus = licenceStatus
                    val oldFeatures = addOnFeatures
                    registerInventory(purchases, newPurchase)

                    if (newPurchase || oldStatus != licenceStatus || addOnFeatures.size > oldFeatures.size) {
                        (activity as? BillingListener)?.onLicenceStatusSet(prettyPrintStatus(activity))
                    }
                }
                return licenceStatus != null || addOnFeatures.isNotEmpty()
            }

            override fun onPurchaseCanceled() {
                log().i("onPurchasesUpdated() - user cancelled the purchase flow - skipping")
                (activity as? ContribInfoDialogActivity)?.onPurchaseCancelled()
            }

            override fun onPurchaseFailed(resultCode: Int) {
                log().w("onPurchasesUpdated() got unknown resultCode: %s", resultCode)
                (activity as? ContribInfoDialogActivity)?.onPurchaseFailed(resultCode)
            }
        }
        val skuDetailsResponseListener = if (query) SkuDetailsResponseListener { result: BillingResult, skuDetailsList: List<SkuDetails>? ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                storeSkuDetails(skuDetailsList)
            } else {
                log().w("skuDetails response %d", result.responseCode)
            }
        } else null
        return BillingManagerPlay(activity, billingUpdatesListener, skuDetailsResponseListener)
    }

    @VisibleForTesting
    fun findHighestValidPurchase(inventory: List<Purchase>) = inventory.mapNotNull { purchase -> extractLicenceStatusFromSku(purchase.sku)?.let { Pair(purchase, it) } }
            .maxByOrNull { pair -> pair.second }?.first

    private fun registerInventory(inventory: List<Purchase>, newPurchase: Boolean) {
        inventory.forEach { purchase: Purchase -> log().i("%s (acknowledged %b)", purchase.sku, purchase.isAcknowledged) }
        findHighestValidPurchase(inventory)?.let {
            if (it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                handlePurchaseForLicence(it.sku, it.orderId, it.purchaseToken)
            } else {
                CrashHandler.reportWithTag(String.format("Found purchase in state %s", it.purchaseState), TAG)
            }
        } ?: run {
            if (!newPurchase) {
                maybeCancel()
            }
        }
        handlePurchaseForAddOns(inventory.map { Pair(Licence.parseFeature(it.sku), it.orderId) }, newPurchase)
        licenseStatusPrefs.commit()
    }

    private fun handlePurchaseForAddOns(features: List<Pair<AddOnPackage?, String>>, newPurchase: Boolean) {
        val newFeatures = features.filter { pair ->
            pair.first?.let {
                persistOrderIdForAddOn(it, pair.second)
                true
            } ?: false
        }.map { it.first!!.feature }
        maybeUpgradeAddonFeatures(newFeatures, newPurchase)
    }

    override fun launchPurchase(aPackage: Package, shouldReplaceExisting: Boolean, billingManager: BillingManager) {
        val sku = getSkuForPackage(aPackage)
        val skuDetails = getSkuDetailsFromPrefs(sku)
                ?: throw IllegalStateException("Could not determine sku details for $sku")
        val oldPurchase: Pair<String, String>?
        if (shouldReplaceExisting) {
            oldPurchase = currentSubscription
            checkNotNull(oldPurchase) { "Could not determine current subscription" }
            check(sku != oldPurchase.first)
        } else {
            oldPurchase = null
        }
        (billingManager as BillingManagerPlay).initiatePurchaseFlow(skuDetails, oldPurchase)
    }
}