package ua.rytm.app.data

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.ShoppingItemEntity
import ua.rytm.app.data.local.SyncOutboxEntity
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
    private val saveMutex = Mutex()
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

    suspend fun saveSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val local = db.shoppingDao().getAllOnce(uid, profileId)
        financeDocRef(uid, profileId).set(
            mapOf("shoppingList" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun queueSnapshot(uid: String, profileId: String, mutation: suspend () -> Unit) {
        db.withTransaction {
            mutation()
            db.syncOutboxDao().upsert(
                SyncOutboxEntity(
                    operationId = UUID.randomUUID().toString(), ownerUid = uid, profileId = profileId,
                    domain = OUTBOX_DOMAIN_SHOPPING, entityId = OUTBOX_SNAPSHOT, operation = OUTBOX_SNAPSHOT,
                    payload = null, createdAt = System.currentTimeMillis(),
                ),
            )
        }
        context?.let(::scheduleSyncOutbox)
    }

    suspend fun drainOutbox(limit: Int = 100): Boolean {
        var failed = false
        db.syncOutboxDao().oldestForDomain(OUTBOX_DOMAIN_SHOPPING, limit).forEach { operation ->
            runCatching { saveSnapshot(operation.ownerUid, operation.profileId) }
                .onSuccess { db.syncOutboxDao().delete(operation.operationId) }
                .onFailure { error ->
                    failed = true
                    val failure = SyncFailure.from(error)
                    SafeDiagnostics.reportSync(SafeDiagnostics.Domain.SHOPPING, failure)
                    db.syncOutboxDao().markFailed(operation.operationId, failure.diagnosticCode)
                }
        }
        return !failed && db.syncOutboxDao().countForDomain(OUTBOX_DOMAIN_SHOPPING) == 0
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
