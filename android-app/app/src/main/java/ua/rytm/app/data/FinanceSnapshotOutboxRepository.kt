package ua.rytm.app.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.SyncOutboxEntity
import ua.rytm.app.data.local.SyncRevisionEntity
import ua.rytm.app.work.scheduleSyncOutbox
import java.util.UUID

class FinanceSnapshotOutboxRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val context: Context? = null,
) {
    val operationState: Flow<TransactionSyncState?> = RoomProfileScope.changes.flatMapLatest { scope ->
        observeState(scope.ownerUid, scope.profileId)
    }

    private fun ref(uid: String, profileId: String) = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun queue(uid: String, profileId: String, field: String, value: Any?) {
        require(field in SUPPORTED_FIELDS)
        val prior = db.syncOutboxDao().getForEntity(uid, profileId, DOMAIN, field)
        val baseRevision = prior?.payload?.let(::baseRevision)
            ?: db.syncRevisionDao().get(uid, profileId, DOMAIN, field)
            ?: 0L
        val payload = JSONObject()
            .put("field", field)
            .put("baseRevision", baseRevision)
            .put("value", JSONObject.wrap(value))
            .toString()
        db.syncOutboxDao().upsert(
            SyncOutboxEntity(
                UUID.randomUUID().toString(), uid, profileId, DOMAIN, field, "upsert", payload,
                System.currentTimeMillis(),
            ),
        )
        context?.let(::scheduleSyncOutbox)
    }

    suspend fun hasPending(uid: String, profileId: String, field: String): Boolean =
        db.syncOutboxDao().getForEntity(uid, profileId, DOMAIN, field) != null

    suspend fun rememberRemoteRevision(uid: String, profileId: String, field: String, revision: Long) {
        require(field in SUPPORTED_FIELDS && revision >= 0)
        if (!hasPending(uid, profileId, field)) {
            db.syncRevisionDao().upsert(SyncRevisionEntity(uid, profileId, DOMAIN, field, revision))
        }
    }

    fun observeState(uid: String, profileId: String): Flow<TransactionSyncState?> =
        db.syncOutboxDao().observe(uid, profileId, DOMAIN).map { rows ->
            when {
                rows.any { it.lastErrorCode != null } -> TransactionSyncState.ERROR
                rows.isNotEmpty() -> TransactionSyncState.PENDING
                else -> null
            }
        }

    suspend fun drainOutbox(limit: Int = 100): Boolean {
        var failed = false
        db.syncOutboxDao().oldestForDomain(DOMAIN, limit).forEach { operation ->
            runCatching { upload(operation) }
                .onSuccess { revision ->
                    db.syncRevisionDao().upsert(
                        SyncRevisionEntity(operation.ownerUid, operation.profileId, DOMAIN, operation.entityId, revision),
                    )
                    db.syncOutboxDao().delete(operation.operationId)
                }
                .onFailure { error ->
                    failed = true
                    val failure = if (error.causes().any { it is SnapshotRevisionConflict }) {
                        SyncFailure(SyncFailure.Kind.CONFLICT, false, "SYNC_CONFLICT")
                    } else SyncFailure.from(error)
                    SafeDiagnostics.reportSync(SafeDiagnostics.Domain.FINANCE, failure)
                    db.syncOutboxDao().markFailed(operation.operationId, failure.diagnosticCode)
                }
        }
        return !failed && db.syncOutboxDao().countForDomain(DOMAIN) == 0
    }

    private suspend fun upload(operation: SyncOutboxEntity): Long {
        val payload = JSONObject(checkNotNull(operation.payload))
        val field = payload.getString("field")
        require(field == operation.entityId && field in SUPPORTED_FIELDS)
        val expected = payload.getLong("baseRevision")
        val value = payload.get("value").toKotlinValue()
        val document = ref(operation.ownerUid, operation.profileId)
        return firestore.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            val actual = snapshot.getLong("fieldRevisions.$field") ?: 0L
            if (actual != expected) throw SnapshotRevisionConflict()
            transaction.set(
                document,
                mapOf(
                    field to value,
                    "fieldRevisions" to mapOf(field to expected + 1),
                    "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            )
            expected + 1
        }.await()
    }

    private class SnapshotRevisionConflict : RuntimeException()

    companion object {
        const val DOMAIN = "finance_snapshots"
        val SUPPORTED_FIELDS = setOf(
            "wallets", "categories", "subcategories", "categoryIcons", "budgets", "tags",
            "recurring", "goals", "currencyRates", "autoRules",
        )
    }
}

private fun baseRevision(payload: String): Long = JSONObject(payload).optLong("baseRevision", 0L)

private fun Throwable.causes(): List<Throwable> =
    generateSequence(this as Throwable?) { it.cause }.take(8).filterNotNull().toList()

private fun Any?.toKotlinValue(): Any? = when (this) {
    JSONObject.NULL -> null
    is JSONObject -> keys().asSequence().associateWith { get(it).toKotlinValue() }
    is JSONArray -> (0 until length()).map { get(it).toKotlinValue() }
    else -> this
}
