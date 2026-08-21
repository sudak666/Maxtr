package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.CurrencyRateEntity
import ua.rytm.app.data.local.RytmDatabase

// Same one-time cold-sync bootstrap pattern as categoryIcons, applied to
// `currencyRates` (js/core.js's AppState.currencyRates, Record<code,
// rateToUAH> — confirmed by reading convertCurrency()). Unlike every other
// synced domain, this one is never pushed as a device-local seed on a
// first-time account: the PWA itself only ever writes currencyRates via
// updateRatesOnline()/updateCurrencyRate() (Settings → FX rates), never
// from a bare "no remote doc yet" bootstrap branch — so an empty/missing
// remote value here just means the app falls back to SEED_RATES client-side
// (see FinanceRepository.convertCurrency()), not something to seed back.
class CurrencyRatesSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncCurrencyRatesOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteRates = snapshot.get("currencyRates") as? Map<*, *>
        if (remoteRates != null) {
            val entities = remoteRates.mapNotNull { (code, rate) ->
                val c = code as? String ?: return@mapNotNull null
                val r = (rate as? Number)?.toDouble() ?: return@mapNotNull null
                CurrencyRateEntity(code = c, rateToUah = r)
            }
            db.currencyRateDao().replaceAll(entities)
        }
    }
}
