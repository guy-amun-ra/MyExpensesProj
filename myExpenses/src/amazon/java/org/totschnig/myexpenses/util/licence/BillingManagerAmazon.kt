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

import android.content.Context
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.util.licence.LicenceHandler.Companion.log

class BillingManagerAmazon(val context: Context, private val mBillingUpdatesListener: AmazonBillingUpdatesListener) : BillingManager {
    var initialPurchasesRequestId: RequestId? = null

    init {
       log().d("BillingManagerAmazon init.")

        PurchasingService.registerListener(context, object : PurchasingListener {
            override fun onProductDataResponse(productDataResponse: ProductDataResponse) {
                val status = productDataResponse.requestStatus
                log().d("onProductDataResponse() reqStatus: %s, reqId: %s", status, productDataResponse.requestId)
                when (status) {
                    ProductDataResponse.RequestStatus.SUCCESSFUL -> {
                        mBillingUpdatesListener.onProductDataResponse(productDataResponse.productData)
                    }
                    else -> {
                        mBillingUpdatesListener.onBillingSetupFailed("Unable to fetch product data")
                    }
                }
            }

            override fun onPurchaseResponse(purchaseResponse: PurchaseResponse) {
                val status = purchaseResponse.requestStatus
                log().d("onPurchaseResponse() reqStatus: %s, reqId: %s", status, purchaseResponse.requestId)
                when (status) {
                    PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                        with (purchaseResponse.receipt) {
                            if (mBillingUpdatesListener.onPurchase(this)) {
                                PurchasingService.notifyFulfillment(receiptId, FulfillmentResult.FULFILLED)
                            }
                        }
                    }
                    else -> mBillingUpdatesListener.onPurchaseFailed(status)
                }
            }

            override fun onPurchaseUpdatesResponse(purchaseUpdatesResponse: PurchaseUpdatesResponse) {
                val status = purchaseUpdatesResponse.requestStatus
                val requestId = purchaseUpdatesResponse.requestId
                log().d("onPurchaseUpdatesResponse() reqStatus: %s, reqId: %s", status, requestId)

                when (status) {
                    PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL -> {
                        mBillingUpdatesListener.onPurchasesUpdated(purchaseUpdatesResponse.receipts, requestId.equals(initialPurchasesRequestId))
                        if (purchaseUpdatesResponse.hasMore()) {
                            fetchPurchaseUpdates()
                        }
                    }
                    else ->  mBillingUpdatesListener.onBillingSetupFailed("Unable to fetch purchase updates")
                }
            }

            override fun onUserDataResponse(userDataResponse: UserDataResponse) {
                val requestStatus = userDataResponse.requestStatus
                log().d("onUserDataResponse() reqStatus: %s, reqId: %s", requestStatus, userDataResponse.requestId)
                when (requestStatus) {
                    UserDataResponse.RequestStatus.SUCCESSFUL -> {
                        mBillingUpdatesListener.onBillingSetupFinished()
                    }
                    else -> {
                        mBillingUpdatesListener.onBillingSetupFailed(
                                "Please make sure you are logged into Amazon AppStore (%s)"
                                        .format(requestStatus.name))
                    }
                }

            }

        })
    }

    override fun onResume(query: Boolean) {
        log().d("BillingManagerAmazon onResume 1.")
        PurchasingService.getUserData()
        if (query) {
            log().d("BillingManagerAmazon onResume 2.")
            fetchPurchaseUpdates()
            PurchasingService.getProductData(Config.amazonSkus.toSet())
        }
    }

    private fun fetchPurchaseUpdates() {
        if (initialPurchasesRequestId == null) {
            initialPurchasesRequestId = PurchasingService.getPurchaseUpdates(true)
        } else {
            PurchasingService.getPurchaseUpdates(false)
        }
    }

    /**
     * Start a purchase or subscription replace flow
     */
    fun initiatePurchaseFlow(sku: String) {
        PurchasingService.purchase(sku)
    }
}

interface AmazonBillingUpdatesListener {
    fun onPurchasesUpdated(purchases: MutableList<Receipt>, initialFullRequest: Boolean)
    fun onProductDataResponse(productData: MutableMap<String, Product>)
    fun onPurchase(receipt: Receipt) : Boolean
    fun onPurchaseFailed(resultCode: PurchaseResponse.RequestStatus)
    fun onBillingSetupFailed(message: String)
    fun onBillingSetupFinished()
}