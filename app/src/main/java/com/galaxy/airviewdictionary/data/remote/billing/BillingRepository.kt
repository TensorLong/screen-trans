package com.galaxy.airviewdictionary.data.remote.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.galaxy.airviewdictionary.data.AVDRepository
import com.galaxy.airviewdictionary.data.remote.firebase.FireDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BillingRepository @Inject constructor(@ApplicationContext val context: Context) : AVDRepository() {

    companion object {
        const val PRODUCT_ID = "premium_auto"
    }

    private var queryPurchasesJob: Job? = null

    private val _purchaseStateFlow = MutableStateFlow(Purchase.PurchaseState.UNSPECIFIED_STATE)

    val purchaseStateFlow: StateFlow<Int> get() = _purchaseStateFlow

    private val _purchaseStateMessageFlow = MutableStateFlow("")

    val purchaseStateMessageFlow = _purchaseStateMessageFlow.asStateFlow()

    val productDetailsFlow = MutableStateFlow<ProductDetails?>(null)

    private lateinit var billingClient: BillingClient

    private lateinit var purchasesUpdatedListener: PurchasesUpdatedListener

    private var isBillingClientConnecting = false

    private var isBillingClientReady = false

    private fun initPurchasesUpdatedListener() {
        if (!::purchasesUpdatedListener.isInitialized) {
            purchasesUpdatedListener =
                PurchasesUpdatedListener { billingResult: BillingResult, purchases: List<Purchase>? ->
                    handlePurchase(billingResult, purchases)
                }
        }
    }

    private fun startBillingClientConnection(context: Context, attempts: Int = 0) {
        if (isBillingClientConnecting || isBillingClientReady) {
            Timber.tag(TAG).w("BillingClient already connecting or connected. Skipping connection request.")
            return
        }

        Timber.tag(TAG).i("================ startBillingClientConnection attempts : ${attempts}")
        initPurchasesUpdatedListener()
        isBillingClientConnecting = true

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
//            .enablePendingPurchases()
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            // .setEnablePendingPurchases(true)
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isBillingClientConnecting = false

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isBillingClientReady = true
                    // The BillingClient is ready. You can query purchases here.
                    launchInAVDCoroutineScope {
                        queryProductDetails()
                        queryPurchases()
                        startPurchaseQueryLoop()
                    }
                } else if (
                    billingResult.responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR
                    || billingResult.responseCode == BillingClient.BillingResponseCode.ERROR
                ) {
                    if (attempts < 10) {
                        launchInAVDCoroutineScope {
                            delay((2000 + 1000 * attempts).toLong())
                            startBillingClientConnection(context, attempts + 1)
                        }
                    } else {
                        Timber.tag(TAG).e("startBillingClientConnection completely failed after $attempts attempts.")
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to Google Play by calling the startConnection() method.
                startBillingClientConnection(context, 0)
            }
        })
    }

    private suspend fun queryProductDetails(attempts: Int = 0) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
        params.setProductList(productList)

        val productDetailsResult: ProductDetailsResult = withContext(Dispatchers.IO) {
            billingClient.queryProductDetails(params.build())
        }

        val billingResult: BillingResult = productDetailsResult.billingResult
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val productDetailsList: List<ProductDetails>? = productDetailsResult.productDetailsList
            /*
                {
                    "productId": "premium",
                    "type": "subs",
                    "title": "premium (Screen Translator)",
                    "name": "premium",
                    "localizedIn": [
                        "en-US"
                    ],
                    "skuDetailsToken": "AEuhp4JO4CB0ip1LUysw78k3ntXBQ88EBUMNzRYHGvxdisqnPkfzwLwjI6wL7B3oBoce",
                    "subscriptionOfferDetails": [
                        {
                            "offerIdToken": "AeiR4ts9tYr6Q8zxghWupHhp3lq8cKWlKD1oVWRGVeqVxOq3YaJ5CZIIUlvqWT6ck4uaLFE/A9qupPNVtYk8",
                            "basePlanId": "premium-0",
                            "pricingPhases": [
                                {
                                    "priceAmountMicros": 4900000000,
                                    "priceCurrencyCode": "KRW",
                                    "formattedPrice": "₩4,900",
                                    "billingPeriod": "P1M",
                                    "recurrenceMode": 3
                                }
                            ],
                            "offerTags": []
                        }
                    ]
                }
             */
            productDetailsList?.forEach { productDetails ->
                val productId = productDetails.productId
                val productName = productDetails.name ?: "Unknown product name"
                val productType = productDetails.productType ?: "Unknown product type"
                val offerToken = productDetails.subscriptionOfferDetails?.first()?.offerToken

                // 가격 정보가 있는지 확인 후 출력
                val priceDetails = productDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    ?: productDetails.oneTimePurchaseOfferDetails?.formattedPrice
                    ?: "Unknown price"

                Timber.tag(TAG).d(
                    """
                    Product ID: $productId
                    Product Name: $productName
                    Product Type: $productType
                    Price: $priceDetails
                    offerToken: $offerToken
                    """.trimIndent()
                )
            }

            productDetailsFlow.value = productDetailsList?.firstOrNull()

        } else if (
            billingResult.responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR
            || billingResult.responseCode == BillingClient.BillingResponseCode.ERROR
        ) {
            if (attempts < 10) {
                delay((2000 + 1000 * attempts).toLong())
                queryProductDetails(attempts + 1)
            }
        }
    }

    private fun queryPurchases() {
        Timber.tag(TAG).i("================ queryPurchases ")
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult: BillingResult, purchases: List<Purchase> ->
            handlePurchase(billingResult, purchases)
        }
    }

    private fun startPurchaseQueryLoop(
        initialDelay: Long = 24 * 60 * 60 * 1000L,
        nextDelay: Long = 24 * 60 * 60 * 1000L
    ) {
        Timber.tag(TAG).i("================ startPurchaseQueryLoop initialDelay $initialDelay")
        queryPurchasesJob = launchInAVDCoroutineScope {
            delay(initialDelay)  // 첫 번째 딜레이 (예: 2분 또는 24시간)
            queryPurchases()  // 첫 번째 실행
            Timber.tag(TAG).i("================ startPurchaseQueryLoop nextDelay $nextDelay")
            while (true) {
                delay(nextDelay)  // 그 이후로는 설정된 지연 시간 (24시간)
                queryPurchases()
            }
        }
    }

    private fun stopPurchaseQueryLoop() {
        Timber.tag(TAG).i("================ stopPurchaseQueryLoop ")
        queryPurchasesJob?.cancel()
    }

    fun restartPurchaseQueryLoop(initialDelay: Long = 60 * 10 * 1000L) {
        Timber.tag(TAG).i("================ restartPurchaseQueryLoop : ${initialDelay}")
        stopPurchaseQueryLoop()
        startPurchaseQueryLoop(initialDelay = initialDelay, nextDelay = 24 * 60 * 60 * 1000L)
    }

    fun launchBillingFlow(activity: Activity) {
        val productDetails: ProductDetails = productDetailsFlow.value ?: throw IllegalStateException("productDetails is null")
        val offerToken = productDetails.subscriptionOfferDetails?.first()?.offerToken ?: throw IllegalStateException("offerToken is null")

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                .setProductDetails(productDetails)
                // For One-time products, "setOfferToken" method shouldn't be called.
                // For subscriptions, to get an offer token, call ProductDetails.subscriptionOfferDetails()
                // for a list of offers that are available to the user
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        // Launch the billing flow
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(billingResult: BillingResult, purchases: List<Purchase>?) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage

        Timber.d(
            """
            BillingResult:
            Response Code: $responseCode
            Debug Message: $debugMessage
            """.trimIndent()
        )
        printPurchases(purchases)

        /*
            BillingResult에는 BillingResponseCode가 포함되어 있어 앱에서 발생할 수 있는 결제 관련 오류를 분류합니다.
            예를 들어, SERVICE_DISCONNECTED 오류 코드가 수신되면 앱에서 Google Play와의 연결을 다시 초기화해야 합니다.
            또한 BillingResult에는 개발 중에 오류를 진단하는 데 유용한 디버그 메시지가 포함되어 있습니다.
         */
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases.isNullOrEmpty()) {
                _purchaseStateFlow.value = Purchase.PurchaseState.UNSPECIFIED_STATE
                _purchaseStateMessageFlow.value = ""
            } else {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (purchase.isAcknowledged) {
                            _purchaseStateFlow.value = Purchase.PurchaseState.PURCHASED
                        } else {
                            acknowledgePurchase(purchase)
                        }
                    } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                        _purchaseStateFlow.value = Purchase.PurchaseState.PENDING
                        _purchaseStateMessageFlow.value = "Purchase Success (pending)"
                    } else {
                        _purchaseStateFlow.value = Purchase.PurchaseState.UNSPECIFIED_STATE
                        _purchaseStateMessageFlow.value = "Purchase Failed"
                    }
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user canceling the purchase flow.
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
            _purchaseStateMessageFlow.value = "Purchase Failed"
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) {
            startBillingClientConnection(context)
        } else {
            _purchaseStateMessageFlow.value = "Purchase Failed"
        }
    }

    private fun printPurchases(purchases: List<Purchase>?) {
        if (purchases.isNullOrEmpty()) {
            Timber.i("No purchases available.")
            return
        }

        purchases.forEach { purchase ->
            Timber.d(
                """
                Purchase Details:
                Purchase Token: ${purchase.purchaseToken}
                Order ID: ${purchase.orderId ?: "N/A"}
                Product IDs: ${purchase.products.joinToString(", ")}
                Purchase State: ${purchase.purchaseState}
                Is Acknowledged: ${purchase.isAcknowledged}
                Is AutoRenewing: ${purchase.isAutoRenewing}
                Original JSON: ${purchase.originalJson}
                """.trimIndent()
            )
        }
    }

    private fun acknowledgePurchase(purchase: Purchase, attempts: Int = 0) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult: BillingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _purchaseStateFlow.value = Purchase.PurchaseState.PURCHASED
                _purchaseStateMessageFlow.value = "Purchase Success"
                FireDatabase.purchaseReport(context, purchase)
                restartPurchaseQueryLoop()
            } else if (
                billingResult.responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR
                || billingResult.responseCode == BillingClient.BillingResponseCode.ERROR
            ) {
                launchInAVDCoroutineScope {
                    delay((1000 + 1000 * attempts).toLong())
                    startBillingClientConnection(context, attempts + 1)
                }
            }
        }
    }

    init {
        startBillingClientConnection(context)
    }

    override fun onZeroReferences() {
        stopPurchaseQueryLoop()
        if (::billingClient.isInitialized) {
            billingClient.endConnection() // BillingClient 연결 해제
        }
        isBillingClientReady = false
        isBillingClientConnecting = false
    }
}









