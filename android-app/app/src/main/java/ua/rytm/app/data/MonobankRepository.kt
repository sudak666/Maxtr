package ua.rytm.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ua.rytm.app.data.local.AutoRuleEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.MonobankTokenStore
import ua.rytm.app.data.local.TransactionEntity
import ua.rytm.app.data.local.WalletEntity
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

data class MonobankAccount(val id: String, val kind: String, val label: String, val currency: String)
data class MonobankConnection(
    val token: String,
    val clientName: String,
    val accounts: List<MonobankAccount>,
    val mapping: Map<String, String>,
    val lastSyncAt: Long?,
)
data class MonobankSyncProgress(val current: Int, val total: Int)
class MonobankHttpException(val status: Int, message: String) : Exception(message)

class MonobankRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val tokenStore: MonobankTokenStore,
    private val requestOverride: (suspend (String, Map<String, String>, String) -> Any)? = null,
    private val requestGapMs: Long = REQUEST_GAP_MS,
) {
    private val requestMutex = Mutex()
    private var lastRequestAt = 0L

    private fun financeRef(uid: String, profileId: String) = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun load(uid: String, profileId: String): MonobankConnection? {
        val map = financeRef(uid, profileId).get().await().get("integrations.monobank") as? Map<*, *> ?: return null
        val legacyToken = map["token"] as? String
        if (!legacyToken.isNullOrBlank()) {
            tokenStore.write(uid, profileId, legacyToken)
            financeRef(uid, profileId).update("integrations.monobank.token", FieldValue.delete()).await()
        }
        val token = tokenStore.read(uid, profileId) ?: return null
        return parseConnection(map, token)
    }

    suspend fun connect(uid: String, profileId: String, rawToken: String): MonobankConnection {
        val token = rawToken.trim()
        require(token.isNotBlank()) { "Введіть токен Monobank" }
        val info = request("client-info", emptyMap(), token) as? JSONObject ?: error("Некоректна відповідь Monobank")
        val accounts = buildAccounts(info)
        require(accounts.isNotEmpty()) { "У Monobank не знайдено карток або банок" }
        val existing = db.walletDao().getAllOnce()
        val palette = listOf(0xFF8B5CF6, 0xFF10B981, 0xFF3B82F6, 0xFFF59E0B, 0xFFEC4899, 0xFF06B6D4)
        val wallets = accounts.mapIndexed { index, account ->
            WalletEntity(
                id = UUID.randomUUID().toString(),
                name = "Monobank ${account.label}",
                colorHex = palette[(existing.size + index) % palette.size],
                currency = account.currency,
                icon = if (account.kind == "jar") "target" else "card",
            )
        }
        db.walletDao().insertAll(wallets)
        val connection = MonobankConnection(token, info.optString("name"), accounts, accounts.mapIndexed { i, a -> a.id to wallets[i].id }.toMap(), null)
        tokenStore.write(uid, profileId, token)
        try {
            save(uid, profileId, connection, existing + wallets)
        } catch (error: Throwable) {
            tokenStore.delete(uid, profileId)
            wallets.forEach { db.walletDao().deleteById(it.id) }
            throw error
        }
        return connection
    }

    suspend fun disconnect(uid: String, profileId: String) {
        financeRef(uid, profileId).set(
            mapOf("integrations" to mapOf("monobank" to null), "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        ).await()
        tokenStore.delete(uid, profileId)
    }

    suspend fun sync(
        uid: String,
        profileId: String,
        connection: MonobankConnection,
        onProgress: (MonobankSyncProgress) -> Unit,
    ): Pair<MonobankConnection, Int> {
        val entries = connection.mapping.entries.toList()
        require(entries.isNotEmpty()) { "У Monobank не знайдено рахунків" }
        val nowSec = System.currentTimeMillis() / 1000L
        val fromSec = connection.lastSyncAt ?: nowSec - MAX_WINDOW_SEC
        val knownIds = db.transactionDao().getAllMonobankIds().toMutableSet()
        val rules = db.autoRuleDao().getAllOnce()
        val currencies = db.walletDao().getAllOnce().associate { it.id to it.currency }
        val imported = mutableListOf<TransactionEntity>()

        entries.forEachIndexed { index, entry ->
            onProgress(MonobankSyncProgress(index + 1, entries.size))
            var start = fromSec
            var chunks = 0
            while (start < nowSec && chunks < MAX_CHUNKS) {
                val end = minOf(nowSec, start + MAX_WINDOW_SEC)
                val rows = request("statement", mapOf("account" to entry.key, "from" to start.toString(), "to" to end.toString()), connection.token) as? JSONArray
                    ?: error("Некоректна відповідь Monobank")
                for (rowIndex in 0 until rows.length()) {
                    buildTransaction(rows.getJSONObject(rowIndex), entry.value, currencies[entry.value] ?: "UAH", rules, knownIds, imported.size)?.let(imported::add)
                }
                start = end
                chunks++
            }
        }
        if (imported.isNotEmpty()) {
            val collection = financeRef(uid, profileId).collection("transactions")
            imported.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.set(collection.document(it.id), it.toRemoteMap()) }
                batch.commit().await()
            }
            db.transactionDao().insertAll(imported)
        }
        val updated = connection.copy(lastSyncAt = nowSec)
        save(uid, profileId, updated)
        return updated to imported.size
    }

    private fun buildTransaction(
        row: JSONObject,
        walletId: String,
        currency: String,
        rules: List<AutoRuleEntity>,
        knownIds: MutableSet<String>,
        offset: Int,
    ): TransactionEntity? {
        if (row.optBoolean("hold")) return null
        val monobankId = row.optString("id")
        if (monobankId.isBlank() || !knownIds.add(monobankId)) return null
        val signedMinor = row.optLong("amount")
        val amount = abs(signedMinor) / 100.0
        if (!amount.isFinite() || amount <= 0.0) return null
        val type = if (signedMinor < 0) "EXPENSE" else "INCOME"
        val comment = row.optString("description").trim()
        val category = rules.firstOrNull {
            it.type.equals(type, ignoreCase = true) && it.keyword.isNotEmpty() && comment.contains(it.keyword, ignoreCase = true)
        }?.category ?: "Інше"
        return TransactionEntity(
            id = UUID.randomUUID().toString(), type = type, amount = amount, currency = currency,
            date = Instant.ofEpochSecond(row.getLong("time")).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            walletId = walletId, targetWalletId = null, targetAmount = null, targetCurrency = null,
            category = category, subcategory = null, comment = comment, tags = "",
            createdAt = System.currentTimeMillis() + offset, monobankId = monobankId,
        )
    }

    private suspend fun save(uid: String, profileId: String, value: MonobankConnection, wallets: List<WalletEntity>? = null) {
        val body = mutableMapOf<String, Any?>(
            "integrations" to mapOf("monobank" to value.toRemoteMap()),
            "updatedAt" to System.currentTimeMillis(),
        )
        if (wallets != null) body["wallets"] = wallets.map { it.toRemoteWalletMap() }
        financeRef(uid, profileId).set(body, SetOptions.merge()).await()
    }

    private suspend fun request(action: String, params: Map<String, String>, token: String): Any = requestMutex.withLock {
        requestOverride?.let { return@withLock it(action, params, token) }
        val waitMs = requestGapMs - (System.currentTimeMillis() - lastRequestAt)
        if (waitMs > 0) delay(waitMs)
        try {
            val firebaseToken = auth.currentUser?.getIdToken(false)?.await()?.token ?: error("Потрібно увійти в акаунт")
            val query = (mapOf("action" to action) + params).entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
            withContext(Dispatchers.IO) {
                val connection = URL("https://maxtr-c238f.web.app/api/monobank?$query").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 30_000
                    connection.setRequestProperty("Authorization", "Bearer $firebaseToken")
                    connection.setRequestProperty("X-Monobank-Token", token)
                    val status = connection.responseCode
                    val text = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (status !in 200..299) throw MonobankHttpException(status, runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty().ifBlank { "Monobank HTTP $status" })
                    if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
                } finally {
                    connection.disconnect()
                }
            }
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun buildAccounts(info: JSONObject): List<MonobankAccount> {
        val result = mutableListOf<MonobankAccount>()
        val accounts = info.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accounts.length()) {
            val item = accounts.getJSONObject(i)
            val pans = item.optJSONArray("maskedPan")
            val label = pans?.optString(0).orEmpty().ifBlank { item.optString("type").takeIf(String::isNotBlank)?.let { "Monobank $it" } ?: "Monobank" }
            result += MonobankAccount(item.getString("id"), "account", label, currency(item.optInt("currencyCode")))
        }
        val jars = info.optJSONArray("jars") ?: JSONArray()
        for (i in 0 until jars.length()) {
            val item = jars.getJSONObject(i)
            result += MonobankAccount(item.getString("id"), "jar", item.optString("title").ifBlank { "Банка" }, currency(item.optInt("currencyCode")))
        }
        return result
    }

    private fun parseConnection(map: Map<*, *>, token: String): MonobankConnection? {
        val accounts = (map["accounts"] as? List<*>)?.mapNotNull { raw ->
            val a = raw as? Map<*, *> ?: return@mapNotNull null
            MonobankAccount(a["id"] as? String ?: return@mapNotNull null, a["kind"] as? String ?: "account", a["label"] as? String ?: "Monobank", a["currencyAlpha"] as? String ?: "UAH")
        }.orEmpty()
        val mapping = (map["mapping"] as? Map<*, *>)?.entries?.mapNotNull { (key, value) -> (key as? String)?.let { it to (value as? String ?: return@mapNotNull null) } }?.toMap().orEmpty()
        return MonobankConnection(token, map["clientName"] as? String ?: "", accounts, mapping, (map["lastSyncAt"] as? Number)?.toLong())
    }

    private fun WalletEntity.toRemoteWalletMap() = mapOf(
        "id" to id, "name" to name, "color" to colorHexToWebString(colorHex), "icon" to icon, "currency" to currency,
    )

    private fun currency(code: Int) = mapOf(980 to "UAH", 840 to "USD", 978 to "EUR", 826 to "GBP", 985 to "PLN")[code] ?: "UAH"
    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val MAX_WINDOW_SEC = 2_682_000L
        const val REQUEST_GAP_MS = 61_000L
        const val MAX_CHUNKS = 14
    }
}

internal fun MonobankConnection.toRemoteMap() = mapOf(
    "clientName" to clientName,
    "accounts" to accounts.map { mapOf("id" to it.id, "kind" to it.kind, "label" to it.label, "currencyAlpha" to it.currency) },
    "mapping" to mapping,
    "lastSyncAt" to lastSyncAt,
)
