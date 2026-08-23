package ua.rytm.app.data

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.SyncOutboxEntity
import ua.rytm.app.data.local.TransactionEntity
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.work.scheduleSyncOutbox
import java.util.UUID

// Same one-time cold-sync bootstrap pattern as Finance/Shifts/CategoriesSyncRepository,
// applied to the `transactions` SUBCOLLECTION (js/firebase-sync.js's
// txCollection()/saveTransactionDoc()) rather than a single field on the `finance`
// doc — see CLAUDE.md's "Transactions live in their own subcollection" section for
// why the PWA itself made this split (1 MiB doc-size risk, whole-doc rewrite per save).
//
// Each transaction doc is written whole (PWA's saveTransactionDoc() itself uses
// setDoc(..., {merge:false}) — safe here because, unlike the shared `finance` doc,
// a transactions/{id} doc has no other-platform-only fields to protect; it's 1:1
// owned by this one feature on both platforms).
//
// Field-name mapping (PWA tx object, js/finance.js's addTransaction(), -> Android
// TransactionEntity): wallet->walletId, targetWallet->targetWalletId, tags (array
// of tag ids) -> comma-joined string (matches this file's existing simplification
// for locally-created transactions, see FinanceEntities.kt's own doc comment —
// Android has no Tag entity yet either).
enum class TransactionSyncState { PENDING, ERROR }

class TransactionsSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val context: Context? = null,
) {
    val operationStates: Flow<Map<String, TransactionSyncState>> = RoomProfileScope.changes.flatMapLatest { scope ->
        observeOperationStates(scope.ownerUid, scope.profileId)
    }

    private fun txCollectionRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))
            .collection("transactions")

    suspend fun syncTransactionsOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val colRef = txCollectionRef(uid, profileId)
        val snapshot = colRef.get().await()
        if (!snapshot.isEmpty) {
            // Remote wins on cold sign-in — same bootstrap direction as every other synced domain.
            val remote = snapshot.documents.mapNotNull { it.data?.let(::parseRemoteTransaction) }
                .map { it.copy(ownerUid = uid, profileId = profileId) }
            val pending = db.syncOutboxDao().get(uid, profileId, OUTBOX_DOMAIN)
            val pendingIds = pending.mapTo(mutableSetOf()) { it.entityId }
            val localPending = db.transactionDao().getAllOnce(uid, profileId).filter { it.id in pendingIds }
            db.transactionDao().replaceAll(remote.filterNot { it.id in pendingIds } + localPending, uid, profileId)
        } else {
            // First-time account (no transactions subcollection yet) — push this
            // device's local transactions up as the seed, chunked the same way
            // js/firebase-sync.js's batchWriteTransactions() is (Firestore batches
            // cap at 500 ops; well under that here).
            val local = db.transactionDao().getAllOnce(uid, profileId)
            local.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { tx -> batch.set(colRef.document(tx.id), tx.toRemoteMap()) }
                batch.commit().await()
            }
        }
    }

    suspend fun saveTransaction(uid: String, profileId: String, transaction: TransactionEntity) {
        txCollectionRef(uid, profileId).document(transaction.id).set(transaction.toRemoteMap()).await()
    }

    suspend fun deleteTransaction(uid: String, profileId: String, id: String) {
        txCollectionRef(uid, profileId).document(id).delete().await()
    }

    suspend fun deleteTransactions(uid: String, profileId: String, ids: Collection<String>) {
        ids.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id -> batch.delete(txCollectionRef(uid, profileId).document(id)) }
            batch.commit().await()
        }
    }

    suspend fun saveTransactions(uid: String, profileId: String, transactions: List<TransactionEntity>) {
        transactions.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { tx -> batch.set(txCollectionRef(uid, profileId).document(tx.id), tx.toRemoteMap()) }
            batch.commit().await()
        }
    }

    fun observeOperationStates(uid: String, profileId: String): Flow<Map<String, TransactionSyncState>> =
        db.syncOutboxDao().observe(uid, profileId, OUTBOX_DOMAIN).map { operations ->
            operations.associate { it.entityId to if (it.lastErrorCode == null) TransactionSyncState.PENDING else TransactionSyncState.ERROR }
        }

    suspend fun queueSave(uid: String, profileId: String, transaction: TransactionEntity) {
        db.withTransaction {
            val existing = db.transactionDao().getById(transaction.id, uid, profileId)
            val prior = db.syncOutboxDao().getForEntity(uid, profileId, OUTBOX_DOMAIN, transaction.id)
            val baseRevision = prior?.baseRevision() ?: existing?.revision ?: REVISION_MISSING
            val scoped = transaction.copy(
                ownerUid = uid, profileId = profileId,
                revision = maxOf(existing?.revision ?: 0, transaction.revision) + 1,
                updatedAt = System.currentTimeMillis(),
            )
            db.transactionDao().upsert(scoped)
            db.syncOutboxDao().upsert(scoped.toOutbox(OUTBOX_UPSERT, baseRevision))
        }
        context?.let(::scheduleSyncOutbox)
    }

    suspend fun queueSaves(uid: String, profileId: String, transactions: List<TransactionEntity>) {
        db.withTransaction {
            transactions.forEach { transaction ->
                val existing = db.transactionDao().getById(transaction.id, uid, profileId)
                val prior = db.syncOutboxDao().getForEntity(uid, profileId, OUTBOX_DOMAIN, transaction.id)
                val baseRevision = prior?.baseRevision() ?: existing?.revision ?: REVISION_MISSING
                val scoped = transaction.copy(
                    ownerUid = uid, profileId = profileId,
                    revision = maxOf(existing?.revision ?: 0, transaction.revision) + 1,
                    updatedAt = System.currentTimeMillis(),
                )
                db.transactionDao().upsert(scoped)
                db.syncOutboxDao().upsert(scoped.toOutbox(OUTBOX_UPSERT, baseRevision))
            }
        }
        context?.let(::scheduleSyncOutbox)
    }

    suspend fun queueDeletes(uid: String, profileId: String, ids: Collection<String>) {
        db.withTransaction {
            ids.forEach { id ->
                val existing = db.transactionDao().getById(id, uid, profileId)
                val prior = db.syncOutboxDao().getForEntity(uid, profileId, OUTBOX_DOMAIN, id)
                val baseRevision = prior?.baseRevision() ?: existing?.revision ?: REVISION_MISSING
                db.transactionDao().deleteById(id, uid, profileId)
                db.syncOutboxDao().upsert(
                    SyncOutboxEntity(
                        UUID.randomUUID().toString(), uid, profileId, OUTBOX_DOMAIN, id, OUTBOX_DELETE,
                        JSONObject().put("baseRevision", baseRevision).toString(), System.currentTimeMillis(),
                    ),
                )
            }
        }
        context?.let(::scheduleSyncOutbox)
    }

    suspend fun drainOutbox(limit: Int = 100): Boolean {
        var failed = false
        db.syncOutboxDao().oldestForDomain(OUTBOX_DOMAIN, limit).forEach { operation ->
            runCatching {
                when (operation.operation) {
                    OUTBOX_UPSERT -> saveRevisioned(operation, transactionFromOutbox(checkNotNull(operation.payload)))
                    OUTBOX_DELETE -> deleteRevisioned(operation)
                    else -> error("Unknown outbox operation")
                }
            }.onSuccess {
                db.syncOutboxDao().delete(operation.operationId)
            }.onFailure { error ->
                if (error.causes().any { it is TransactionRevisionConflict }) {
                    resolveConflict(operation)
                } else {
                    failed = true
                    val failure = SyncFailure.from(error)
                    SafeDiagnostics.reportSync(SafeDiagnostics.Domain.TRANSACTIONS, failure)
                    db.syncOutboxDao().markFailed(operation.operationId, failure.diagnosticCode)
                }
            }
        }
        return !failed && db.syncOutboxDao().countForDomain(OUTBOX_DOMAIN) == 0
    }

    private suspend fun saveRevisioned(operation: SyncOutboxEntity, transaction: TransactionEntity) {
        val ref = txCollectionRef(operation.ownerUid, operation.profileId).document(operation.entityId)
        firestore.runTransaction { remoteTransaction ->
            val snapshot = remoteTransaction.get(ref)
            requireRevision(snapshot.exists(), snapshot.getLong("revision"), operation.baseRevision())
            remoteTransaction.set(ref, transaction.toRemoteMap())
        }.await()
    }

    private suspend fun deleteRevisioned(operation: SyncOutboxEntity) {
        val ref = txCollectionRef(operation.ownerUid, operation.profileId).document(operation.entityId)
        firestore.runTransaction { remoteTransaction ->
            val snapshot = remoteTransaction.get(ref)
            requireRevision(snapshot.exists(), snapshot.getLong("revision"), operation.baseRevision())
            if (snapshot.exists()) remoteTransaction.delete(ref)
        }.await()
    }

    private suspend fun resolveConflict(operation: SyncOutboxEntity) {
        val snapshot = txCollectionRef(operation.ownerUid, operation.profileId)
            .document(operation.entityId).get(Source.SERVER).await()
        db.withTransaction {
            if (operation.operation == OUTBOX_UPSERT) {
                val local = transactionFromOutbox(checkNotNull(operation.payload))
                if (snapshot.exists()) {
                    snapshot.data?.let { data ->
                        parseRemoteTransaction(data + ("id" to operation.entityId))
                            ?.copy(ownerUid = operation.ownerUid, profileId = operation.profileId)
                            ?.let { db.transactionDao().upsert(it) }
                    }
                    val conflictCopy = local.copy(
                        id = "conflict_${UUID.randomUUID().toString().replace("-", "").take(24)}",
                        revision = 1,
                        updatedAt = System.currentTimeMillis(),
                    )
                    db.transactionDao().upsert(conflictCopy)
                    db.syncOutboxDao().upsert(conflictCopy.toOutbox(OUTBOX_UPSERT, REVISION_MISSING))
                } else {
                    db.transactionDao().upsert(local)
                    db.syncOutboxDao().upsert(local.toOutbox(OUTBOX_UPSERT, REVISION_MISSING))
                }
            } else if (snapshot.exists()) {
                snapshot.data?.let { data ->
                    parseRemoteTransaction(data + ("id" to operation.entityId))
                        ?.copy(ownerUid = operation.ownerUid, profileId = operation.profileId)
                        ?.let { db.transactionDao().upsert(it) }
                }
            }
            db.syncOutboxDao().delete(operation.operationId)
        }
        SafeDiagnostics.reportSync(
            SafeDiagnostics.Domain.TRANSACTIONS,
            SyncFailure(SyncFailure.Kind.CONFLICT, false, "SYNC_CONFLICT"),
        )
    }
}

private const val OUTBOX_DOMAIN = "transactions"
private const val OUTBOX_UPSERT = "upsert"
private const val OUTBOX_DELETE = "delete"
private const val REVISION_MISSING = -1L

private fun TransactionEntity.toOutbox(operation: String, baseRevision: Long) = SyncOutboxEntity(
    operationId = UUID.randomUUID().toString(), ownerUid = ownerUid, profileId = profileId,
    domain = OUTBOX_DOMAIN, entityId = id, operation = operation,
    payload = toOutboxJson().put("baseRevision", baseRevision).toString(), createdAt = System.currentTimeMillis(),
)

private fun SyncOutboxEntity.baseRevision(): Long = payload?.let { JSONObject(it).optLong("baseRevision", REVISION_MISSING) } ?: REVISION_MISSING

private fun Throwable.causes(): List<Throwable> = generateSequence(this as Throwable?) { it.cause }.take(8).filterNotNull().toList()

private fun requireRevision(exists: Boolean, remoteRevision: Long?, expected: Long) {
    val actual = if (exists) remoteRevision ?: 0L else REVISION_MISSING
    if (actual != expected) throw TransactionRevisionConflict()
}

private class TransactionRevisionConflict : RuntimeException()

private fun TransactionEntity.toOutboxJson() = JSONObject().apply {
    put("id", id); put("type", type); put("amount", amount); put("currency", currency); put("date", date); put("walletId", walletId)
    put("targetWalletId", targetWalletId); put("targetAmount", targetAmount); put("targetCurrency", targetCurrency); put("category", category)
    put("subcategory", subcategory); put("comment", comment); put("tags", tags); put("createdAt", createdAt); put("monobankId", monobankId)
    put("ownerUid", ownerUid); put("profileId", profileId)
    put("revision", revision); put("updatedAt", updatedAt)
}

private fun transactionFromOutbox(payload: String): TransactionEntity = JSONObject(payload).run {
    fun nullableString(key: String) = if (isNull(key)) null else getString(key)
    fun nullableDouble(key: String) = if (isNull(key)) null else getDouble(key)
    TransactionEntity(
        id = getString("id"), type = getString("type"), amount = getDouble("amount"), currency = getString("currency"), date = getString("date"),
        walletId = getString("walletId"), targetWalletId = nullableString("targetWalletId"), targetAmount = nullableDouble("targetAmount"),
        targetCurrency = nullableString("targetCurrency"), category = getString("category"), subcategory = nullableString("subcategory"),
        comment = nullableString("comment"), tags = getString("tags"), createdAt = getLong("createdAt"), monobankId = nullableString("monobankId"),
        ownerUid = getString("ownerUid"), profileId = getString("profileId"),
        revision = optLong("revision", 0), updatedAt = optLong("updatedAt", getLong("createdAt")),
    )
}

internal fun TransactionEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "createdAt" to createdAt,
    "type" to type.lowercase(),
    "amount" to amount,
    "currency" to currency,
    "category" to category,
    "subcategory" to subcategory,
    "tags" to if (tags.isBlank()) emptyList<String>() else tags.split(","),
    "wallet" to walletId,
    "targetWallet" to targetWalletId,
    "targetAmount" to targetAmount,
    "targetCurrency" to targetCurrency,
    "date" to date,
    "comment" to comment,
    "monobankId" to monobankId,
    "revision" to revision,
    "updatedAt" to updatedAt,
)

private fun parseRemoteTransaction(m: Map<String, Any?>): TransactionEntity? {
    val id = m["id"]?.toString() ?: return null
    val type = (m["type"] as? String)?.uppercase() ?: return null
    val amount = (m["amount"] as? Number)?.toDouble() ?: return null
    val date = m["date"] as? String ?: return null
    val wallet = m["wallet"] as? String ?: return null
    val tags = (m["tags"] as? List<*>)?.filterIsInstance<String>()?.joinToString(",") ?: ""
    return TransactionEntity(
        id = id,
        type = type,
        amount = amount,
        currency = m["currency"] as? String ?: "UAH",
        date = date,
        walletId = wallet,
        targetWalletId = m["targetWallet"] as? String,
        targetAmount = (m["targetAmount"] as? Number)?.toDouble(),
        targetCurrency = m["targetCurrency"] as? String,
        category = m["category"] as? String ?: "",
        subcategory = m["subcategory"] as? String,
        comment = m["comment"] as? String,
        tags = tags,
        createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
        monobankId = m["monobankId"] as? String,
        revision = (m["revision"] as? Number)?.toLong() ?: 0L,
        updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: ((m["createdAt"] as? Number)?.toLong() ?: 0L),
    )
}
