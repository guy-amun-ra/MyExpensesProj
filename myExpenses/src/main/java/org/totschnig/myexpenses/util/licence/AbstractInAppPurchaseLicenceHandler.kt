package org.totschnig.myexpenses.util.licence

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.util.crashreporting.CrashHandler


abstract class AbstractInAppPurchaseLicenceHandler(
    context: Application,
    preferenceObfuscator: PreferenceObfuscator,
    crashHandler: CrashHandler,
    prefHandler: PrefHandler,
    repository: Repository
) : ContribStatusLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler, repository) {
    val pricesPrefs: SharedPreferences = context.getSharedPreferences(PRICES_PREFS_FILE, Context.MODE_PRIVATE)

    override fun getLegacyStatus() = STATUS_ENABLED_LEGACY_SECOND

    override fun init() {
        super.init()
        d("init")
        readContribStatusFromPrefs()
    }
    override fun getFormattedPrice(aPackage: Package): String? {
        val pricesPrefsString = getDisplayPriceForPackage(aPackage)
        return if (pricesPrefsString != null)
            aPackage.getFormattedPrice(context, pricesPrefsString, false)
        else
            null
    }

    protected abstract fun getDisplayPriceForPackage(aPackage: Package): String?

    /**
     * @param sku
     * @return which LicenceStatus an sku gives access to
     */
    fun extractLicenceStatusFromSku(sku: String): LicenceStatus? {
        if (sku.contains(LicenceStatus.PROFESSIONAL.toSkuType())) return LicenceStatus.PROFESSIONAL
        if (sku.contains(LicenceStatus.EXTENDED.toSkuType())) return LicenceStatus.EXTENDED
        return if (sku.contains(LicenceStatus.CONTRIB.toSkuType())) LicenceStatus.CONTRIB else null
    }

    /**
     * After 2 days, if purchase cannot be verified, we set back
     */
    fun maybeCancel() {
        if (contribStatus != STATUS_ENABLED_LEGACY_SECOND) {
            if (System.currentTimeMillis() - licenseStatusPrefs.getString(prefHandler.getKey(PrefKey.LICENSE_INITIAL_TIMESTAMP), "0").toLong() > REFUND_WINDOW) {
                cancel()
            }
        }
    }

    fun cancel() {
        updateContribStatus(STATUS_DISABLED)
    }

    fun handlePurchaseForLicence(sku: String, orderId: String, purchaseToken: String? = null) {
        log().i("handling $sku")
        licenseStatusPrefs.putString(KEY_ORDER_ID, orderId)
        extractLicenceStatusFromSku(sku).also {
            when (it) {
                LicenceStatus.CONTRIB -> registerPurchase(false)
                LicenceStatus.EXTENDED -> registerPurchase(true)
                LicenceStatus.PROFESSIONAL -> registerSubscription(sku, purchaseToken)
                else -> {}
            }
        }
    }

    /**
     * @param extended if true user has purchase extended licence
     */
    @VisibleForTesting
    fun registerPurchase(extended: Boolean) {
        var status = if (extended) STATUS_EXTENDED_TEMPORARY else STATUS_ENABLED_TEMPORARY
        val timestampKey = prefHandler.getKey(PrefKey.LICENSE_INITIAL_TIMESTAMP)
        val timestamp = licenseStatusPrefs.getString(timestampKey, "0").toLong()
        val now = System.currentTimeMillis()
        if (timestamp == 0L) {
            licenseStatusPrefs.putString(timestampKey, now.toString())
        } else {
            val timeSincePurchase = now - timestamp
            log().d("time since initial check : %d", timeSincePurchase)
            //give user 2 days to request refund
            if (timeSincePurchase > REFUND_WINDOW) {
                status = if (extended) STATUS_EXTENDED_PERMANENT else STATUS_ENABLED_PERMANENT
            }
        }
        updateContribStatus(status)
    }

    private fun registerSubscription(sku: String, purchaseToken: String?) {
        licenseStatusPrefs.putString(KEY_CURRENT_SUBSCRIPTION_SKU, sku)
        purchaseToken?.let {
            licenseStatusPrefs.putString(KEY_CURRENT_SUBSCRIPTION_PURCHASE_TOKEN, it)
        }
        updateContribStatus(STATUS_PROFESSIONAL)
    }

    fun getSkuForPackage(aPackage: Package): String {
        val hasExtended = licenceStatus != null && licenceStatus == LicenceStatus.EXTENDED
        return when (aPackage) {
            Package.Contrib -> Config.SKU_PREMIUM
            Package.Upgrade -> Config.SKU_PREMIUM2EXTENDED
            Package.Extended -> Config.SKU_EXTENDED
            ProfessionalPackage.Professional_1 -> Config.SKU_PROFESSIONAL_1
            ProfessionalPackage.Professional_12 -> Config.SKU_PROFESSIONAL_12
            ProfessionalPackage.Amazon -> Config.SKU_PROFESSIONAL_PARENT
            is AddOnPackage -> aPackage.sku
            else -> throw IllegalStateException("Did not find sku for package $aPackage")
        }
    }

    override fun getProLicenceStatus(context: Context) =
            when (licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION_SKU, "")) {
                Config.SKU_PROFESSIONAL_1, Config.SKU_EXTENDED2PROFESSIONAL_1 -> R.string.monthly_plain
                Config.SKU_PROFESSIONAL_12, Config.SKU_EXTENDED2PROFESSIONAL_12 -> R.string.yearly_plain
                else -> 0
            }.takeIf { it != 0 }?.let { context.getString(it) }

    override fun buildRoadmapVoteKey() =
        purchaseExtraInfo.takeIf { isProfessionalEnabled } ?: super.buildRoadmapVoteKey()

    protected fun handlePurchaseForAddOns(
        features: List<AddOnPackage>,
        newPurchase: Boolean
    ) {
        maybeUpgradeAddonFeatures(features.map { it.feature }, newPurchase)
    }

    override val purchaseExtraInfo: String?
        get() = licenseStatusPrefs.getString(KEY_ORDER_ID, null)

    override val doesUseIAP: Boolean
        get() = true

    override val needsKeyEntry: Boolean
        get() = false

    companion object {
        const val KEY_CURRENT_SUBSCRIPTION_SKU = "current_subscription"
        const val KEY_CURRENT_SUBSCRIPTION_PURCHASE_TOKEN = "current_subscription_purchase_token"
        private const val KEY_ORDER_ID = "order_id"
        private const val REFUND_WINDOW = 172800000L
        private const val STATUS_DISABLED = 0
        private const val PRICES_PREFS_FILE = "license_prices"
    }
}
