package org.totschnig.myexpenses.util.licence

import android.app.Application
import android.content.Context
import com.amazon.device.iap.model.Product
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.Receipt
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity
import org.totschnig.myexpenses.activity.IapActivity
import org.totschnig.myexpenses.contrib.Config.amazonSkus
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class StoreLicenceHandler(
    context: Application,
    preferenceObfuscator: PreferenceObfuscator,
    crashHandler: CrashHandler,
    prefHandler: PrefHandler,
    repository: Repository
) :
        AbstractInAppPurchaseLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler, repository) {

    override fun initBillingManager(activity: IapActivity, query: Boolean): BillingManager {
        val billingUpdatesListener = object : AmazonBillingUpdatesListener {
            override fun onPurchase(receipt: Receipt): Boolean {
                handlePurchaseForLicence(receipt.sku, receipt.receiptId)
                handlePurchaseForAddOns(
                    listOfNotNull(Licence.parseFeature(receipt.sku)),
                    true
                )
                (activity as? BillingListener)?.onLicenceStatusSet(prettyPrintStatus(activity))
                return licenceStatus != null
            }

            override fun onPurchasesUpdated(purchases: MutableList<Receipt>, initialFullRequest: Boolean) {
                val oldStatus = licenceStatus
                registerInventory(purchases, initialFullRequest)
                if (oldStatus != licenceStatus) {
                    (activity as? BillingListener)?.onLicenceStatusSet(prettyPrintStatus(activity))
                }
            }

            override fun onProductDataResponse(productData: MutableMap<String, Product>) {
                storeSkuDetails(productData)
            }

            override fun onPurchaseFailed(resultCode: PurchaseResponse.RequestStatus) {
                log().w("onPurchaseFailed() resultCode: %s", resultCode)
                (activity as? ContribInfoDialogActivity)?.onPurchaseFailed(resultCode.ordinal)
            }

            override fun onBillingSetupFailed(message: String) {
                (activity as? BillingListener)?.onBillingSetupFailed(message)
            }

            override fun onBillingSetupFinished() {
                (activity as? BillingListener)?.onBillingSetupFinished()
            }
        }
        return BillingManagerAmazon(activity.applicationContext, billingUpdatesListener)
    }

    private fun registerInventory(purchases: MutableList<Receipt>, initialFullRequest: Boolean) {
        findHighestValidPurchase(purchases)?.let {
            handlePurchaseForLicence(it.sku, it.receiptId)
        } ?: kotlin.run { if (initialFullRequest) maybeCancel() }
        handlePurchaseForAddOns(
            purchases.mapNotNull { Licence.parseFeature(it.sku) },
            !initialFullRequest
        )
    }

    private fun findHighestValidPurchase(purchases: List<Receipt>) =
            purchases.filter { !it.isCanceled && extractLicenceStatusFromSku(it.sku) != null }
                    .maxByOrNull { extractLicenceStatusFromSku(it.sku)?.ordinal ?: 0 }

    private fun storeSkuDetails(productData: MutableMap<String, Product>) {
        val editor = pricesPrefs.edit()
        amazonSkus.forEach { sku ->
            val product = productData[sku]
            product?.let {
                log().d("Sku: %s", it.toString())
                editor.putString(sku, it.price)
            } ?: kotlin.run {
                log().d("Did not find details for %s", sku)
            }
        }
        editor.apply()
    }

    override suspend fun launchPurchase(aPackage: Package, shouldReplaceExisting: Boolean, billingManager: BillingManager) {
        (billingManager as? BillingManagerAmazon)?.initiatePurchaseFlow(getSkuForPackage(aPackage))
    }

    override val proPackages: Array<ProfessionalPackage>
        get() = arrayOf(ProfessionalPackage.Amazon)

    override val professionalPriceShortInfo: String
        get() = joinPriceInformation(
            ProfessionalPackage.Professional_1,
            ProfessionalPackage.Professional_12
        )

    override fun getDisplayPriceForPackage(aPackage: Package) = pricesPrefs.getString(getSkuForPackage(aPackage), null)

    override val proPackagesForExtendOrSwitch: Array<ProfessionalPackage>?
        get() = null

    override fun getProLicenceAction(context: Context) = ""
}