package ua.rytm.app.data

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.ShoppingItemEntity
import ua.rytm.app.data.local.SyncOutboxEntity
import ua.rytm.app.data.local.SyncRevisionEntity
import ua.rytm.app.work.scheduleSyncOutbox
import java.util.UUID

// Same one-time cold-sync bootstrap pattern as Categories/Finance/ShiftsSyncRepository,
// applied to the `finance` doc's `shoppingList` field (js/state.js's
// AppState.shoppingList, `{id,name,qty,done,createdAt}` — confirmed against
// js/shopping.js's addShoppingItem(), a direct 1:1 with ShoppingItemEntity, no
// field-mapping decisions needed unlike wallets/transactions).
//
// Uses SetOptions.merge(true) and only ever touches the `shoppingList`/`updatedAt`
// keys — never a full-doc setDoc(..., {merge:false}) — same safety rule as every
// other synced field on the shared `finance` doc.
class ShoppingSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val context: Context? = null,
) {
    val operationState: Flow<TransactionSyncState?> = RoomProfileScope.changes.flatMapLatest { scope ->
        db.syncOutboxDao().observe(scope.ownerUid, scope.profileId, OUTBOX_DOMAIN_SHOPPING)
    }.map { operations ->
        when {
            operations.isEmpty() -> null
            operations.any { it.lastErrorCode != null } -> TransactionSyncState.ERROR
            else -> TransactionSyncState.PENDING
        }
    }

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncShoppingListOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        if (db.syncOutboxDao().get(uid, profileId, OUTBOX_DOMAIN_SHOPPING).isNotEmpty()) return
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteList = snapshot.get("shoppingList") as? List<*>
        db.syncRevisionDao().upsert(
            SyncRevisionEntity(uid, profileId, OUTBOX_DOMAIN_SHOPPING, OUTBOX_SNAPSHOT, snapshot.getLong("fieldRevisions.shoppingList") ?: 0L),
        )
        if (snapshot.exists() && remoteList != null) {
            // Remote wins on cold sign-in — same bootstrap direction as every other synced domain.
            val entities = remoteList.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteItem) }
                .map { it.copy(ownerUid = uid, profileId = profileId) }
            db.shoppingDao().replaceAll(entities, uid, profileId)
        } else {
            // First-time account (no finance doc yet, or one predating shoppingList
            // syncing) — push this device's local list up as the seed.
            val local = db.shoppingDao().getAllOnce(uid, profileId)
            docRef.set(
                mapOf("shoppingList" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    suspend fun queueSnapshot(uid: String, profileId: String, mutation: suspend () -> Unit) {
        db.withTransaction {
            mutation()
            val prior = db.syncOutboxDao().getForEntity(uid, profileId, OUTBOX_DOMAIN_SHOPPING, OUTBOX_SNAPSHOT)
            val baseRevision = prior?.payload?.let { runCatching { JSONObject(it).getLong("baseRevision") }.getOrNull() }
                ?: db.syncRevisionDao().get(uid, profileId, OUTBOX_DOMAIN_SHOPPING, OUTBOX_SNAPSHOT)
                ?: 0L
            db.syncOutboxDao().upsert(
                SyncOutboxEntity(
                    operationId = UUID.randomUUID().toString(), ownerUid = uid, profileId = profileId,
                    domain = OUTBOX_DOMAIN_SHOPPING, entityId = OUTBOX_SNAPSHOT, operation = OUTBOX_SNAPSHOT,
                    payload = JSONObject().put("baseRevision", baseRevision).toString(), createdAt = System.currentTimeMillis(),
                ),
            )
        }
        context?.let(::scheduleSyncOutbox)
    }

    suspend fun drainOutbox(limit: Int = 100): Boolean {
        var failed = false
        db.syncOutboxDao().oldestForDomain(OUTBOX_DOMAIN_SHOPPING, limit).forEach { operation ->
            runCatching { uploadSnapshot(operation) }
                .onSuccess { revision ->
                    db.syncRevisionDao().upsert(SyncRevisionEntity(operation.ownerUid, operation.profileId, OUTBOX_DOMAIN_SHOPPING, OUTBOX_SNAPSHOT, revision))
                    db.syncOutboxDao().delete(operation.operationId)
                }
                .onFailure { error ->
                    failed = true
                    val failure = if (error.snapshotConflict()) SyncFailure(SyncFailure.Kind.CONFLICT, false, "SYNC_CONFLICT") else SyncFailure.from(error)
                    SafeDiagnostics.reportSync(SafeDiagnostics.Domain.SHOPPING, failure)
                    db.syncOutboxDao().markFailed(operation.operationId, failure.diagnosticCode)
                }
        }
        return !failed && db.syncOutboxDao().countForDomain(OUTBOX_DOMAIN_SHOPPING) == 0
    }

    private suspend fun uploadSnapshot(operation: SyncOutboxEntity): Long {
        val expected = operation.payload?.let { runCatching { JSONObject(it).getLong("baseRevision") }.getOrNull() }
            ?: db.syncRevisionDao().get(operation.ownerUid, operation.profileId, OUTBOX_DOMAIN_SHOPPING, OUTBOX_SNAPSHOT)
            ?: 0L
        val local = db.shoppingDao().getAllOnce(operation.ownerUid, operation.profileId).map { it.toRemoteMap() }
        val ref = financeDocRef(operation.ownerUid, operation.profileId)
        return firestore.runTransaction { transaction ->
            val actual = transaction.get(ref).getLong("fieldRevisions.shoppingList") ?: 0L
            if (actual != expected) throw SnapshotConflictException()
            transaction.set(
                ref,
                mapOf("shoppingList" to local, "fieldRevisions" to mapOf("shoppingList" to expected + 1), "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            )
            expected + 1
        }.await()
    }
}

internal const val OUTBOX_DOMAIN_SHOPPING = "shopping"
private const val OUTBOX_SNAPSHOT = "snapshot"

private fun ShoppingItemEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "qty" to qty,
    "done" to done,
    "createdAt" to createdAt,
)

private fun parseRemoteItem(m: Map<*, *>): ShoppingItemEntity? {
    val id = m["id"] as? String ?: return null
    val name = m["name"] as? String ?: return null
    val qty = (m["qty"] as? Number)?.toInt() ?: 1
    val done = m["done"] as? Boolean ?: false
    val createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L
    return ShoppingItemEntity(id = id, name = name, qty = qty, done = done, createdAt = createdAt)
}
