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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.BuildConfig
import ua.rytm.app.data.local.SettingsStore
import kotlin.coroutines.resume

/** What the Premium dialog shows while the plans load. */
enum class OffersState { LOADING, READY, UNAVAILABLE, FAILED }

data class PremiumOffer(val details: ProductDetails, val offerToken: String, val basePlan: String, val price: String, val hasTrial: Boolean)

/**
 * Google Play subscription state. Premium is the server's answer (the
 * `premium` callable checks each purchase token with the Play Developer API
 * and also grants the owner's own accounts); the device only forwards tokens.
 * Only release builds gate: QA and debug builds cannot buy, because Play
 * sells to ua.rytm.app only.
 */
class PremiumRepository(context: Context, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val gated = GATE_ENABLED && BuildConfig.BUILD_TYPE == "release"
    private val owned = MutableStateFlow<Boolean?>(null)
    private val _offers = MutableStateFlow<List<PremiumOffer>>(emptyList())
    val offers: StateFlow<List<PremiumOffer>> = _offers.asStateFlow()
    private val _offersState = MutableStateFlow(OffersState.LOADING)
    val offersState: StateFlow<OffersState> = _offersState.asStateFlow()
    /** A purchase that did not go through, for the dialog to say why; null = nothing to say. */
    private val _purchaseError = MutableStateFlow<Int?>(null)
    val purchaseError: StateFlow<Int?> = _purchaseError.asStateFlow()
    fun clearPurchaseError() { _purchaseError.value = null }

    /**
     * Offline starts use the last server answer, but only for the account it
     * was given to and only until the subscription's end (a grant: 30 days
     * from the last check). It used to be a bare boolean kept for ever.
     */
    val isPremium: StateFlow<Boolean> = combine(owned, settings.premiumCache) { live, cache ->
        !gated || (live ?: cache?.let { it.uid == FirebaseAuth.getInstance().currentUser?.uid && System.currentTimeMillis() < it.validUntil } ?: false)
    }.stateIn(scope, SharingStarted.Eagerly, !gated)

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener { result, _ ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK, BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh() }
                BillingClient.BillingResponseCode.USER_CANCELED -> Unit
                else -> _purchaseError.value = purchaseErrorFor(result.responseCode)
            }
        }
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
        if (!gated) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            // The cache is per device: never carry one account's Premium into the next.
            owned.value = false
            settings.setPremiumCache(null)
            return
        }
        val active = if (connect()) {
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            val result = client.queryPurchasesAsync(params)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && PRODUCT_ID in it.products }
            } else emptyList()
        } else emptyList()
        // Each token on its own: one Play account can hold a purchase that
        // belongs to another Rytm account (the server refuses it), and that
        // must not stop this account's own answer (a grant, its own purchase).
        var verified: ServerAnswer? = null
        for (purchase in active) {
            val answer = runCatching { callServer(mapOf("action" to "verify", "purchaseToken" to purchase.purchaseToken)) }.getOrNull()
            if (answer?.premium == true && !purchase.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
            }
            if (answer?.premium == true) verified = answer
        }
        // Offline or the server failed: keep the last answer (owned stays null → cache).
        val answer = verified ?: runCatching { callServer(mapOf("action" to "status")) }.getOrNull() ?: return
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        owned.value = answer.premium
        settings.setPremiumCache(if (answer.premium) ua.rytm.app.data.local.PremiumCache(uid, answer.expiresAt ?: (System.currentTimeMillis() + GRANT_OFFLINE_MS)) else null)
    }

    private class ServerAnswer(val premium: Boolean, val expiresAt: Long?)

    private suspend fun callServer(data: Map<String, Any>): ServerAnswer {
        val result = FirebaseFunctions.getInstance().getHttpsCallable("premium").call(data).await().getData() as? Map<*, *>
        return ServerAnswer(result?.get("premium") == true, (result?.get("expiresAt") as? Number)?.toLong())
    }

    suspend fun loadOffers() {
        if (_offers.value.isEmpty()) _offersState.value = OffersState.LOADING
        if (!connect()) { _offersState.value = OffersState.FAILED; return }
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()
        val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build())
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) { _offersState.value = OffersState.FAILED; return }
        val details = result.productDetailsList.orEmpty().firstOrNull() ?: run { _offersState.value = OffersState.UNAVAILABLE; return }
        _offers.value = details.subscriptionOfferDetails.orEmpty().groupBy { it.basePlanId }.values.mapNotNull { plans ->
            val best = plans.firstOrNull { it.offerId != null } ?: plans.firstOrNull() ?: return@mapNotNull null
            val phases = best.pricingPhases.pricingPhaseList
            PremiumOffer(details, best.offerToken, best.basePlanId, phases.last().formattedPrice, phases.any { it.priceAmountMicros == 0L })
        }.sortedBy { it.basePlan != "yearly" }
        _offersState.value = if (_offers.value.isEmpty()) OffersState.UNAVAILABLE else OffersState.READY
    }

    fun buy(activity: Activity, offer: PremiumOffer) {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details).setOfferToken(offer.offerToken).build()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // Ties the purchase to this account; the server refuses it for any other.
        val accountId = java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).setObfuscatedAccountId(accountId).build())
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK, BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh() }
            else -> _purchaseError.value = purchaseErrorFor(result.responseCode)
        }
    }

    private fun purchaseErrorFor(code: Int): Int = when (code) {
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE, BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> ua.rytm.app.R.string.premium_error_network
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> ua.rytm.app.R.string.premium_error_billing
        else -> ua.rytm.app.R.string.premium_error_generic
    }

    companion object {
        const val PRODUCT_ID = "rytm_premium"
        // On since 2026-11-03: rytm_premium exists in Play and purchases are checked on the server.
        const val GATE_ENABLED = true
        /** How long a grant (no expiry of its own) stands offline after the last check. */
        private const val GRANT_OFFLINE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
