/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Converted to Kotlin and adapted
 */
package org.totschnig.myexpenses.util.licence

import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.activity.IapActivity
import org.totschnig.myexpenses.contrib.Config.playInAppSkus
import org.totschnig.myexpenses.contrib.Config.playSubsSkus
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.licence.LicenceHandler.Companion.log
import java.util.*

/**
 * Handles all the interactions with Play Store (via Billing library), maintains connection to
 * it through BillingClient and caches temporary states/data if needed
 */
class BillingManagerPlay(
    private val activity: IapActivity,
    private val billingUpdatesListener: BillingUpdatesListener,
    private val productDetailsResponseListener: ProductDetailsResponseListener?
) : PurchasesUpdatedListener, BillingManager {

    private val scope: CoroutineScope = activity.lifecycleScope

    /** A reference to BillingClient  */
    private var billingClient: BillingClient

    /**
     * True if billing service is connected now.
     */
    private var isServiceConnected: Boolean = false

    private var tokensToBeConsumed: MutableSet<String> = HashSet()

    init {
        log().d("Creating Billing client.")
        billingClient = newBuilder(this.activity).enablePendingPurchases().setListener(this).build()

        log().d("Starting setup.")

        // Start setup. This is asynchronous and the specified listener will be called
        // once setup completes.
        // It also starts to report all the new purchases through onPurchasesUpdated() callback.
        startServiceConnection {
            log().d("Setup successful.")
            productDetailsResponseListener?.let {
                queryPurchases()
                querySkuDetailsAsync(playInAppSkus, playSubsSkus, it)
            }
            (this.activity as? BillingListener)?.onBillingSetupFinished()
        }
    }

    /**
     * Handle a callback that purchases were updated from the Billing library
     */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> onPurchasesUpdated(purchases, true)
            BillingResponseCode.USER_CANCELED -> billingUpdatesListener.onPurchaseCanceled()
            else -> billingUpdatesListener.onPurchaseFailed(billingResult.responseCode)
        }
    }

    private fun onPurchasesUpdated(purchases: List<Purchase>?, newPurchase: Boolean) {
        if (billingUpdatesListener.onPurchasesUpdated(purchases, newPurchase)) {
            purchases?.forEach { purchase ->
                if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    scope.launch {
                        acknowledgePurchase(purchase.purchaseToken)
                    }
                }
            }
        }
    }

    /**
     * Start a purchase or subscription replace flow
     * @param oldPurchase: Pair of sku and purchaseToken
     */
    suspend fun initiatePurchaseFlow(
        sku: String,
        type: String,
        oldPurchase: String?
    ) {
        if (!billingClient.isReady || !isServiceConnected) {
            throw IllegalStateException("Billing Client: isReady: ${billingClient.isReady}, isServiceConnected: $isServiceConnected")
        }
        log().d("Launching in-app purchase flow. Replace old SKU? %s", oldPurchase != null)
        if (!isFeatureSupported(FeatureType.PRODUCT_DETAILS)) {
            throw IllegalStateException("Play Store on your device is outdated. Please update.")
        }
        billingClient.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(sku)
                        .setProductType(type)
                        .build()
                )
            ).build()
        ).productDetailsList?.getOrNull(0)?.also {
            val purchaseParams = BillingFlowParams.newBuilder()
            val productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(it)
            if (type == ProductType.SUBS) {
                productDetailsParams.setOfferToken(
                    it.subscriptionOfferDetails!![0].offerToken
                )
            }

            purchaseParams.setProductDetailsParamsList(listOf(productDetailsParams.build()))

            if (oldPurchase != null) {
                purchaseParams.setSubscriptionUpdateParams(
                    BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                        .setOldPurchaseToken(oldPurchase)
                        .build()
                )
            }
            billingClient.launchBillingFlow(activity, purchaseParams.build())
        } ?: run {
            throw IllegalStateException("Unable to retrieve product details for $sku ($type) ${oldPurchase?.let { "($it)" } ?: ""}")
        }
    }

    /**
     * Clear the resources
     */
    override fun destroy() {
        log().d("Destroying the manager. BillingClient ready? ${billingClient.isReady}")
        billingClient.endConnection()
    }

    private suspend fun querySkuDetailsAsync(
        inAppList: List<String>, subList: List<String>,
        listener: ProductDetailsResponseListener
    ) {
        fun String.mapToProduct(productType: String) =
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(this)
                .setProductType(productType)
                .build()

        suspend fun List<String>.query(productType: String) {
            val result = billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(this.map { it.mapToProduct(productType) }).build()
            )
            result.productDetailsList?.let {
                listener.onProductDetailsResponse(result.billingResult, it)
            } ?: run {
                log().w("Querying $productType returned empty productDetailsList???")
            }
        }
        inAppList.query(ProductType.INAPP)
        subList.query(ProductType.SUBS)
    }

    private suspend fun acknowledgePurchase(purchaseToken: String) {
        // onPurchaseUpdate is called twice for each purchase
        // We remember which tokens we requested to acknowledge
        if (tokensToBeConsumed.contains(purchaseToken)) {
            log().i("Token was already scheduled to be consumed - skipping...")
            return
        }
        tokensToBeConsumed.add(purchaseToken)

        // Creating a runnable from the request to use it inside our connection retry policy below
        val consumeRequest = suspend {
            // Consume the purchase async
            val result = billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
            )
            d("acknowledgePurchase $purchaseToken", result)
            if (result.responseCode != BillingResponseCode.OK) {
                CrashHandler.report(Exception("acknowledgePurchase failed with responseCode ${result.responseCode}"))
            }
        }

        executeServiceRequest(consumeRequest)
    }

    /**
     * Handle a result from querying of purchases and report an updated list to the listener
     */
    private fun onQueryPurchasesFinished(purchases: List<Purchase>?) {
        log().d("Query inventory was successful.")

        // Update the UI and purchases inventory with new list of purchases
        onPurchasesUpdated(purchases, false)
    }

    /**
     * Checks if subscriptions are supported for current client
     *
     * Note: This method does not automatically retry for RESULT_SERVICE_DISCONNECTED.
     * It is only used in unit tests and after queryPurchases execution, which already has
     * a retry-mechanism implemented.
     *
     */
    private fun areSubscriptionsSupported() = isFeatureSupported(FeatureType.SUBSCRIPTIONS)

    private fun isFeatureSupported(featureType: String): Boolean {
        val responseCode = billingClient.isFeatureSupported(featureType).responseCode
        if (responseCode != BillingResponseCode.OK) {
            log().w("feature %s is not supported, response: %d", featureType, responseCode)
        }
        return responseCode == BillingResponseCode.OK
    }

    /**
     * Query purchases across various use cases and deliver the result in a formalized way through
     * a listener
     */
    private suspend fun queryPurchases() {
        val resultList = mutableListOf<Purchase>()
        val purchasesResult: PurchasesResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.INAPP)
                .build()
        )
        log().i(
            "Querying purchases result code: %d, res: %d",
            purchasesResult.billingResult.responseCode, purchasesResult.purchasesList.size
        )
        resultList.addAll(purchasesResult.purchasesList)
        // If there are subscriptions supported, we add subscription rows as well
        if (areSubscriptionsSupported()) {
            val subscriptionResult: PurchasesResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(ProductType.SUBS)
                    .build()
            )
            log().i(
                "Querying subscriptions result code: %d, res: %d",
                subscriptionResult.billingResult.responseCode, subscriptionResult.purchasesList.size
            )
            resultList.addAll(subscriptionResult.purchasesList)
        } else {
            log().i("Skipped subscription purchases query since they are not supported")
        }
        onQueryPurchasesFinished(resultList)
    }

    private fun startServiceConnection(executeOnSuccess: suspend () -> Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val billingResponseCode = billingResult.responseCode
                d("Setup finished", billingResult)
                if (billingResponseCode == BillingResponseCode.OK) {
                    isServiceConnected = true
                    scope.launch {
                        executeOnSuccess()
                    }
                } else {
                    (activity as? BillingListener)?.onBillingSetupFailed(
                        String.format(
                            Locale.ROOT,
                            "%d (%s)",
                            billingResponseCode,
                            billingResult.debugMessage
                        )
                    )
                    isServiceConnected = false
                }
            }

            override fun onBillingServiceDisconnected() {
                log().d("Service disconnected")
                isServiceConnected = false
            }
        })

    }


    private suspend fun executeServiceRequest(runnable: suspend () -> Unit) {
        if (isServiceConnected) {
            runnable()
        } else {
            // If billing service was disconnected, we try to reconnect 1 time.
            startServiceConnection(runnable)
        }
    }

    private fun d(message: String, result: BillingResult) {
        log().w(
            "%s - Response code: %d, Debug message: %s",
            message,
            result.responseCode,
            result.debugMessage
        )
    }
}

interface BillingUpdatesListener {
    //return true if purchases should be acknowledged
    fun onPurchasesUpdated(purchases: List<Purchase>?, newPurchase: Boolean): Boolean

    fun onPurchaseCanceled()
    fun onPurchaseFailed(resultCode: Int)
}