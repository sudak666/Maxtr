package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
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
class CurrencyRatesSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val outbox: FinanceSnapshotOutboxRepository = FinanceSnapshotOutboxRepository(db, firestore),
) {

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncCurrencyRatesOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteRates = snapshot.get("currencyRates") as? Map<*, *>
        outbox.rememberRemoteRevision(uid, profileId, "currencyRates", snapshot.getLong("fieldRevisions.currencyRates") ?: 0L)
        if (remoteRates != null && outbox.hasPending(uid, profileId, "currencyRates")) return
        if (remoteRates != null && !outbox.hasPending(uid, profileId, "currencyRates")) {
            val entities = remoteRates.mapNotNull { (code, rate) ->
                val c = code as? String ?: return@mapNotNull null
                val r = (rate as? Number)?.toDouble() ?: return@mapNotNull null
                CurrencyRateEntity(code = c, rateToUah = r, ownerUid = uid, profileId = profileId)
            }
            db.currencyRateDao().replaceAll(entities, uid, profileId)
        }
    }

    suspend fun saveRate(uid: String, profileId: String, code: String, value: Double) {
        require(code in SEED_RATES && value > 0.0)
        val current = db.currencyRateDao().getAllOnce(uid, profileId).associate { it.code to it.rateToUah }.toMutableMap()
        current[code] = value
        persist(uid, profileId, current)
    }

    suspend fun refreshOnline(uid: String, profileId: String, usePrivatCashRates: Boolean): Long {
        val current = db.currencyRateDao().getAllOnce(uid, profileId).associate { it.code to it.rateToUah }.toMutableMap()
        val nbu = fetchJsonArray("https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json")
        var updated = 0
        SEED_RATES.keys.forEach { code ->
            for (index in 0 until nbu.length()) {
                val entry = nbu.getJSONObject(index)
                if (entry.optString("cc") == code && entry.optDouble("rate") > 0.0) {
                    current[code] = roundRate(entry.getDouble("rate"))
                    updated++
                    break
                }
            }
        }
        check(updated > 0) { "NBU did not return tracked currencies" }

        if (usePrivatCashRates) {
            runCatching {
                val privat = fetchJsonArray("https://maxtr-c238f.web.app/api/privat-rates")
                for (index in 0 until privat.length()) {
                    val entry = privat.getJSONObject(index)
                    val code = entry.optString("ccy")
                    val buy = entry.optDouble("buy")
                    val sale = entry.optDouble("sale")
                    if (code in SEED_RATES && buy > 0.0 && sale > 0.0) current[code] = roundRate((buy + sale) / 2.0)
                }
            }
        }
        persist(uid, profileId, current)
        return System.currentTimeMillis()
    }

    private suspend fun persist(uid: String, profileId: String, rates: Map<String, Double>) {
        db.currencyRateDao().replaceAll(rates.map { CurrencyRateEntity(it.key, it.value, uid, profileId) }, uid, profileId)
        outbox.queue(uid, profileId, "currencyRates", rates)
    }

    private suspend fun fetchJsonArray(url: String): JSONArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun roundRate(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
