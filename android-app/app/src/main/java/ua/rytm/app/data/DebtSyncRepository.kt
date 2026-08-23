package ua.rytm.app.data

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import ua.rytm.app.data.local.DebtEntity
import ua.rytm.app.data.local.DebtEntryEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.SyncOutboxEntity
import ua.rytm.app.data.local.SyncRevisionEntity
import ua.rytm.app.work.scheduleSyncOutbox
import java.util.UUID

// Same one-time cold-sync bootstrap pattern as the rest of this file's siblings,
// applied to the separate top-level `debt` doc (not a field on `finance` — see
// CLAUDE.md's Firebase data model: `users/{uid}/max_tracker/debt`). Real shape,
// confirmed by reading js/color-picker.js's fbSaveNow()/fbLoadNow(): the doc is
// `{data:{debts:[...], currentDebtId}, updatedAt}` — note the `data` wrapper,
// unlike `finance`'s flat top-level fields. Each debt object
// (js/debt.js's addDebt()) is `{id,name,note,currency,startAmount,dueDate,
// entries:[{id,amount,balance,date}]}` — `amount` is a free-form string on the
// PWA side (addDebtEntry() stores the raw input.value), matching
// DebtEntryEntity's own existing `amount: String` field.
//
// `currentDebtId` is carried in each coalesced snapshot operation, matching the
// PWA document without adding mutable selection state to the Room domain model.
//
// Unlike the `finance` doc, `debt` has no other PWA-only fields to protect
// (its only two keys are `data`/`updatedAt`), so SetOptions.merge() here is a
// belt-and-suspenders consistency choice with the rest of this file's
// repositories, not a hard requirement the way it is for `finance`.
class DebtSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val context: Context? = null,
) {
    val operationState: Flow<TransactionSyncState?> = RoomProfileScope.changes.flatMapLatest { scope ->
        db.syncOutboxDao().observe(scope.ownerUid, scope.profileId, OUTBOX_DOMAIN_DEBT)
    }.map { operations ->
        when {
            operations.isEmpty() -> null
            operations.any { it.lastErrorCode != null } -> TransactionSyncState.ERROR
            else -> TransactionSyncState.PENDING
        }
    }

    private fun debtDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("debt", profileId))

    suspend fun syncDebtsOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        if (db.syncOutboxDao().get(uid, profileId, OUTBOX_DOMAIN_DEBT).isNotEmpty()) return
        val docRef = debtDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        db.syncRevisionDao().upsert(
            SyncRevisionEntity(uid, profileId, OUTBOX_DOMAIN_DEBT, OUTBOX_SNAPSHOT, snapshot.getLong("revision") ?: 0L),
        )
        val data = snapshot.get("data") as? Map<*, *>
        val remoteDebts = data?.get("debts") as? List<*>
        if (snapshot.exists() && remoteDebts != null) {
            // Remote wins on cold sign-in — same bootstrap direction as every other synced domain.
            val debts = mutableListOf<DebtEntity>()
            val entries = mutableListOf<DebtEntryEntity>()
            remoteDebts.forEach { d ->
                val m = d as? Map<*, *> ?: return@forEach
                val debt = parseRemoteDebt(m) ?: return@forEach
                debts += debt.copy(ownerUid = uid, profileId = profileId)
                (m["entries"] as? List<*>)?.forEach { e ->
                    (e as? Map<*, *>)?.let(::parseRemoteEntry)?.let { entries += it.copy(debtId = debt.id, ownerUid = uid, profileId = profileId) }
                }
            }
            db.withTransaction {
                db.debtDao().clearAll(uid, profileId)
                db.debtEntryDao().clearAll(uid, profileId)
                db.debtDao().insertAll(debts)
                db.debtEntryDao().insertAll(entries)
            }
        } else {
            // First-time account (no debt doc yet) — push this device's local debts up as the seed.
            val localDebts = db.debtDao().getAllOnce(uid, profileId)
            val localEntries = db.debtEntryDao().getAllOnce(uid, profileId).groupBy { it.debtId }
            val remoteDebtsOut = localDebts.map { it.toRemoteMap(localEntries[it.id].orEmpty()) }
            docRef.set(
                mapOf("data" to mapOf("debts" to remoteDebtsOut, "currentDebtId" to null), "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    suspend fun queueSnapshot(uid: String, profileId: String, currentDebtId: Long?, mutation: suspend () -> Unit) {
        db.withTransaction {
            mutation()
            val prior = db.syncOutboxDao().getForEntity(uid, profileId, OUTBOX_DOMAIN_DEBT, OUTBOX_SNAPSHOT)
            val baseRevision = prior?.payload?.let(::debtBaseRevision)
                ?: db.syncRevisionDao().get(uid, profileId, OUTBOX_DOMAIN_DEBT, OUTBOX_SNAPSHOT)
                ?: 0L
            val payload = JSONObject().put("baseRevision", baseRevision)
            if (currentDebtId == null) payload.put("currentDebtId", JSONObject.NULL) else payload.put("currentDebtId", currentDebtId)
            db.syncOutboxDao().upsert(
                SyncOutboxEntity(
                    UUID.randomUUID().toString(), uid, profileId, OUTBOX_DOMAIN_DEBT, OUTBOX_SNAPSHOT,
                    OUTBOX_SNAPSHOT, payload.toString(), System.currentTimeMillis(),
                ),
            )
        }
        context?.let(::scheduleSyncOutbox)
    }

    suspend fun drainOutbox(limit: Int = 100): Boolean {
        var failed = false
        db.syncOutboxDao().oldestForDomain(OUTBOX_DOMAIN_DEBT, limit).forEach { operation ->
            runCatching { uploadSnapshot(operation) }
                .onSuccess { revision ->
                    db.syncRevisionDao().upsert(SyncRevisionEntity(operation.ownerUid, operation.profileId, OUTBOX_DOMAIN_DEBT, OUTBOX_SNAPSHOT, revision))
                    db.syncOutboxDao().delete(operation.operationId)
                }
                .onFailure { error ->
                    failed = true
                    val failure = if (error.snapshotConflict()) SyncFailure(SyncFailure.Kind.CONFLICT, false, "SYNC_CONFLICT") else SyncFailure.from(error)
                    SafeDiagnostics.reportSync(SafeDiagnostics.Domain.DEBT, failure)
                    db.syncOutboxDao().markFailed(operation.operationId, failure.diagnosticCode)
                }
        }
        return !failed && db.syncOutboxDao().countForDomain(OUTBOX_DOMAIN_DEBT) == 0
    }

    private suspend fun uploadSnapshot(operation: SyncOutboxEntity): Long {
        val payload = operation.payload?.let { runCatching { JSONObject(it) }.getOrNull() }
        val expected = payload?.optLong("baseRevision")
            ?: db.syncRevisionDao().get(operation.ownerUid, operation.profileId, OUTBOX_DOMAIN_DEBT, OUTBOX_SNAPSHOT)
            ?: 0L
        val currentDebtId = when {
            payload != null && !payload.isNull("currentDebtId") -> payload.getLong("currentDebtId")
            payload == null -> operation.payload?.toLongOrNull()
            else -> null
        }
        val debts = db.debtDao().getAllOnce(operation.ownerUid, operation.profileId)
        val entries = db.debtEntryDao().getAllOnce(operation.ownerUid, operation.profileId).groupBy { it.debtId }
        val ref = debtDocRef(operation.ownerUid, operation.profileId)
        return firestore.runTransaction { transaction ->
            val actual = transaction.get(ref).getLong("revision") ?: 0L
            if (actual != expected) throw SnapshotConflictException()
            transaction.set(
                ref,
                mapOf(
                    "data" to mapOf("debts" to debts.map { it.toRemoteMap(entries[it.id].orEmpty()) }, "currentDebtId" to currentDebtId),
                    "revision" to expected + 1,
                    "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            )
            expected + 1
        }.await()
    }
}

internal const val OUTBOX_DOMAIN_DEBT = "debt"
private const val OUTBOX_SNAPSHOT = "snapshot"

private fun debtBaseRevision(payload: String): Long? =
    runCatching { JSONObject(payload).getLong("baseRevision") }.getOrNull()

private fun DebtEntity.toRemoteMap(entries: List<DebtEntryEntity>): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "note" to note,
    "currency" to currency,
    "startAmount" to startAmount,
    "dueDate" to dueDate,
    "entries" to entries.sortedBy { it.id }.map {
        mapOf("id" to it.id, "amount" to it.amount, "balance" to it.balance, "date" to it.date)
    },
)

private fun parseRemoteDebt(m: Map<*, *>): DebtEntity? {
    val id = (m["id"] as? Number)?.toLong() ?: return null
    val name = m["name"] as? String ?: return null
    return DebtEntity(
        id = id,
        name = name,
        note = m["note"] as? String ?: "",
        currency = when (val currency = m["currency"] as? String) { "грн", "₴", null -> "UAH"; else -> currency },
        startAmount = (m["startAmount"] as? Number)?.toDouble() ?: 0.0,
        dueDate = m["dueDate"] as? String ?: "",
    )
}

private fun parseRemoteEntry(m: Map<*, *>): DebtEntryEntity? {
    val id = (m["id"] as? Number)?.toLong() ?: return null
    val amount = m["amount"]?.toString() ?: return null
    val balance = (m["balance"] as? Number)?.toDouble() ?: return null
    val date = m["date"] as? String ?: return null
    // debtId filled in by the caller (copy(debtId=...)) once the parent debt's
    // real id is known — this parse function has no access to it.
    return DebtEntryEntity(id = id, debtId = 0L, amount = amount, balance = balance, date = date)
}
