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

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.*
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.contrib.Config.playInAppSkus
import org.totschnig.myexpenses.contrib.Config.playSubsSkus
import org.totschnig.myexpenses.util.licence.LicenceHandler.Companion.log
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val BILLING_MANAGER_NOT_INITIALIZED = Int.MIN_VALUE

/**
 * Handles all the interactions with Play Store (via Billing library), maintains connection to
 * it through BillingClient and caches temporary states/data if needed
 */
class BillingManagerPlay(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val billingUpdatesListener: BillingUpdatesListener,
    private val productDetailsResponseListener: ProductDetailsResponseListener?
) : PurchasesUpdatedListener, BillingManager {

    /** A reference to BillingClient  */
    private var billingClient: BillingClient

    /**
     * True if billing service is connected now.
     */
    private var isServiceConnected: Boolean = false

    private var tokensToBeConsumed: MutableSet<String> = HashSet()

    /**
     * Returns the value Billing client response code or BILLING_MANAGER_NOT_INITIALIZED if the
     * client connection response was not received yet.
     */
    var billingClientResponseCode = BILLING_MANAGER_NOT_INITIALIZED
        private set

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
                    acknowledgePurchase(purchase.purchaseToken)
                }
            }
        }
    }

    /**
     * Start a purchase or subscription replace flow
     * @param oldPurchase: Pair of sku and purchaseToken
     */
    fun initiatePurchaseFlow(
        sku: String,
        type: String,
        oldPurchase: String?
    ) {
        check(billingClientResponseCode > BILLING_MANAGER_NOT_INITIALIZED) { "Billing manager not yet initialized" }
        val purchaseFlowRequest = suspend {
            log().d("Launching in-app purchase flow. Replace old SKU? %s", oldPurchase != null)
            billingClient.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(sku)
                            .setProductType(type)
                            .build()
                    )
                ).build()
            ).productDetailsList?.get(0)?.let {
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
            }
            Unit
        }

        executeServiceRequest(purchaseFlowRequest)
    }

    /**
     * Clear the resources
     */
    override fun destroy() {
        log().d("Destroying the manager.")
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun querySkuDetailsAsync(
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

        executeServiceRequest(suspend {
            inAppList.query(ProductType.INAPP)
            subList.query(ProductType.SUBS)
        })
    }

    private fun acknowledgePurchase(purchaseToken: String) {
        // If we've already scheduled to consume this token - no action is needed (this could happen
        // if you received the token when querying purchases inside onReceive() and later from
        // onActivityResult()
        if (tokensToBeConsumed.contains(purchaseToken)) {
            log().i("Token was already scheduled to be consumed - skipping...")
            return
        }
        tokensToBeConsumed.add(purchaseToken)

        // Creating a runnable from the request to use it inside our connection retry policy below
        val consumeRequest = suspend {
            // Consume the purchase async
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
            )
            { billingResult -> d("acknowledgePurchase", billingResult) }
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
    private fun areSubscriptionsSupported(): Boolean {
        val responseCode = billingClient.isFeatureSupported(FeatureType.SUBSCRIPTIONS).responseCode
        if (responseCode != BillingResponseCode.OK) {
            log().w("areSubscriptionsSupported() got an error response: %s", responseCode)
        }
        return responseCode == BillingResponseCode.OK
    }

    /**
     * Query purchases across various use cases and deliver the result in a formalized way through
     * a listener
     */
    private suspend fun queryPurchases() {
        val queryToExecute = suspend {
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
            purchasesResult.purchasesList.let { resultList.addAll(it) }
            // If there are subscriptions supported, we add subscription rows as well
            if (areSubscriptionsSupported()) {
                val subscriptionResult = billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(ProductType.SUBS)
                        .build()
                )
                log().i(
                    "Querying subscriptions result code: %d, res: %d",
                    subscriptionResult.billingResult.responseCode, subscriptionResult.purchasesList.size
                )
                subscriptionResult.purchasesList.let { resultList.addAll(it) }
            } else {
                log().i("Skipped subscription purchases query since they are not supported")
            }
            onQueryPurchasesFinished(resultList)
        }
        executeServiceRequest(queryToExecute)
    }

    private fun startServiceConnection(executeOnSuccess: suspend () -> Unit) {
        scope.launch {
            isServiceConnected = withContext(Dispatchers.IO) {
                suspendCoroutine { continuation ->
                    billingClient.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            val billingResponseCode = billingResult.responseCode
                            d("Setup finished", billingResult)
                            billingClientResponseCode = billingResponseCode
                            if (billingResponseCode == BillingResponseCode.OK) {
                                continuation.resume(true)
                            } else {
                                (activity as? BillingListener)?.onBillingSetupFailed(
                                    String.format(
                                        Locale.ROOT,
                                        "%d (%s)",
                                        billingResponseCode,
                                        billingResult.debugMessage
                                    )
                                )
                                continuation.resume(false)
                            }
                        }

                        override fun onBillingServiceDisconnected() {
                            continuation.resume(false)
                        }
                    })
                }
            }
            if (isServiceConnected) {
                executeOnSuccess()
            }
        }
    }


    private fun executeServiceRequest(runnable: suspend () -> Unit) {
        if (isServiceConnected) {
            scope.launch {
                runnable()
            }
        } else {
            // If billing service was disconnected, we try to reconnect 1 time.
            startServiceConnection(runnable)
        }
    }

    private fun d(message: String, result: BillingResult) {
        log().d(
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