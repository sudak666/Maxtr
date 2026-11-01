package ua.rytm.app.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import ua.rytm.app.BuildConfig
import ua.rytm.app.data.local.SettingsStore
import kotlin.coroutines.resume

data class PremiumOffer(val details: ProductDetails, val offerToken: String, val basePlan: String, val price: String, val hasTrial: Boolean)

/**
 * Google Play subscription state. The purchase is checked on the device for
 * now; a server check through the Play Developer API still has to be added
 * before this can be treated as tamper-proof. Only release builds gate:
 * QA and debug builds cannot buy, because Play sells to ua.rytm.app only.
 */
class PremiumRepository(context: Context, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val gated = GATE_ENABLED && BuildConfig.BUILD_TYPE == "release"
    private val owned = MutableStateFlow<Boolean?>(null)
    private val _offers = MutableStateFlow<List<PremiumOffer>>(emptyList())
    val offers: StateFlow<List<PremiumOffer>> = _offers.asStateFlow()

    /** The last known answer covers offline starts until Play replies. */
    val isPremium: StateFlow<Boolean> = combine(owned, settings.premiumCached) { live, cached -> !gated || (live ?: cached) }
        .stateIn(scope, SharingStarted.Eagerly, !gated)

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener { result, _ -> if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { refresh() } }
        .build()

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    suspend fun refresh() {
        if (!gated || !connect()) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val active = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && PRODUCT_ID in it.products }
        active.filterNot { it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build())
        }
        owned.value = active.isNotEmpty()
        settings.setPremiumCached(active.isNotEmpty())
    }

    suspend fun loadOffers() {
        if (!connect()) return
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()
        val details = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build())
            .productDetailsList.orEmpty().firstOrNull() ?: return
        _offers.value = details.subscriptionOfferDetails.orEmpty().groupBy { it.basePlanId }.values.mapNotNull { plans ->
            val best = plans.firstOrNull { it.offerId != null } ?: plans.firstOrNull() ?: return@mapNotNull null
            val phases = best.pricingPhases.pricingPhaseList
            PremiumOffer(details, best.offerToken, best.basePlanId, phases.last().formattedPrice, phases.any { it.priceAmountMicros == 0L })
        }.sortedBy { it.basePlan != "yearly" }
    }

    fun buy(activity: Activity, offer: PremiumOffer) {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details).setOfferToken(offer.offerToken).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
    }

    companion object {
        const val PRODUCT_ID = "rytm_premium"
        // Off until the merchant account and the Play product exist (owner, 2026-11-01).
        const val GATE_ENABLED = false
    }
}
