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
import ua.rytm.app.data.local.AutoFillScheduleEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.ShiftDayEntity
import ua.rytm.app.data.local.ShiftTypeEntity
import ua.rytm.app.data.local.SyncOutboxEntity
import ua.rytm.app.data.local.SyncRevisionEntity
import ua.rytm.app.work.scheduleSyncOutbox
import java.util.UUID

// Second Firestore sync slice (see FinanceSyncRepository for the first,
// wallets) — same one-time cold-sync-on-sign-in shape, same safety rule:
// SetOptions.merge() touching ONLY the `shiftTypes`/`updatedAt` keys of the
// `shifts` doc, never a full-doc replace. The PWA's `shifts` doc also carries
// `data` (the actual calendar day→shiftType assignments) and
// `autoFillSchedule` — neither is touched here, so this can never wipe a
// user's calendar. Mirrors js/color-picker.js's fbSaveNow() write shape:
// setDoc(userDoc('shifts'), {data, shiftTypes, autoFillSchedule, updatedAt}).
class ShiftsSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val context: Context? = null,
) {
    val operationState: Flow<TransactionSyncState?> = RoomProfileScope.changes.flatMapLatest { scope ->
        db.syncOutboxDao().observe(scope.ownerUid, scope.profileId, OUTBOX_DOMAIN_SHIFTS)
    }.map { operations ->
        when {
            operations.isEmpty() -> null
            operations.any { it.lastErrorCode != null } -> TransactionSyncState.ERROR
            else -> TransactionSyncState.PENDING
        }
    }

    private fun shiftsDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("shifts", profileId))

    suspend fun syncShiftTypesOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        if (hasPending(uid, profileId)) return
        val docRef = shiftsDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        rememberRevision(uid, profileId, snapshot.getLong("revision") ?: 0L)
        val remoteTypes = snapshot.get("shiftTypes") as? List<*>
        if (snapshot.exists() && remoteTypes != null) {
            val entities = remoteTypes.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteShiftType) }
                .map { it.copy(ownerUid = uid, profileId = profileId) }
            db.shiftTypeDao().replaceAll(entities, uid, profileId)
        } else {
            val local = db.shiftTypeDao().getAllOnce(uid, profileId)
            docRef.set(
                mapOf("shiftTypes" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    // Third slice of the `shifts` doc — the actual calendar day→shiftType
    // assignments (js/state.js's AppState.shifts, `Record<dateKey, string[]>`,
    // written under the `data` key — see js/color-picker.js's fbSaveNow()).
    // Same SetOptions.merge() safety rule, touching only `data`/`updatedAt` —
    // `shiftTypes`/`autoFillSchedule` are never touched here.
    suspend fun syncShiftDaysOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        if (hasPending(uid, profileId)) return
        val docRef = shiftsDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        rememberRevision(uid, profileId, snapshot.getLong("revision") ?: 0L)
        val remoteData = snapshot.get("data") as? Map<*, *>
        if (snapshot.exists() && remoteData != null) {
            val entities = mutableListOf<ShiftDayEntity>()
            remoteData.forEach { (dateKey, ids) ->
                val key = dateKey as? String ?: return@forEach
                (ids as? List<*>)?.forEach { id -> (id as? String)?.let { entities += ShiftDayEntity(key, it, uid, profileId) } }
            }
            db.shiftDayDao().replaceAll(entities, uid, profileId)
        } else {
            val local = db.shiftDayDao().getAllOnce(uid, profileId)
            val remoteMap = local.groupBy({ it.dateKey }, { it.shiftTypeId })
            docRef.set(
                mapOf("data" to remoteMap, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    // A missing remote schedule is equivalent to the disabled local default.
    suspend fun syncAutoFillScheduleOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        if (hasPending(uid, profileId)) return
        val docRef = shiftsDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        rememberRevision(uid, profileId, snapshot.getLong("revision") ?: 0L)
        val remote = snapshot.get("autoFillSchedule") as? Map<*, *>
        if (snapshot.exists() && remote != null) {
            db.autoFillScheduleDao().upsert(
                AutoFillScheduleEntity(
                    id = 0,
                    enabled = remote["enabled"] as? Boolean ?: false,
                    typeId = remote["typeId"] as? String ?: "",
                    pattern = remote["pattern"] as? String ?: "every",
                    anchorDate = remote["anchorDate"] as? String ?: "",
                    ownerUid = uid,
                    profileId = profileId,
                ),
            )
        } else {
            val local = db.autoFillScheduleDao().getOnce(uid, profileId) ?: AutoFillScheduleEntity(id = 0, enabled = false, typeId = "", pattern = "every", anchorDate = "", ownerUid = uid, profileId = profileId)
            docRef.set(
                mapOf(
                    "autoFillSchedule" to mapOf(
                        "enabled" to local.enabled,
                        "typeId" to local.typeId,
                        "pattern" to local.pattern,
                        "anchorDate" to local.anchorDate,
                    ),
                    "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            ).await()
        }
    }

    suspend fun saveShiftTypes(uid: String, profileId: String = DEFAULT_PROFILE_ID) = queueSnapshot(uid, profileId) {}

    suspend fun saveShiftDays(uid: String, profileId: String = DEFAULT_PROFILE_ID) = queueSnapshot(uid, profileId) {}

    suspend fun saveAutoFillSchedule(uid: String, profileId: String = DEFAULT_PROFILE_ID) = queueSnapshot(uid, profileId) {}

    suspend fun saveShiftTypesAndDays(uid: String, profileId: String = DEFAULT_PROFILE_ID) = queueSnapshot(uid, profileId) {}

    suspend fun saveAutoFillAndDays(uid: String, profileId: String = DEFAULT_PROFILE_ID) = queueSnapshot(uid, profileId) {}

    suspend fun queueSnapshot(uid: String, profileId: String, mutation: suspend () -> Unit) {
        db.withTransaction {
            mutation()
            val prior = db.syncOutboxDao().getForEntity(uid, profileId, OUTBOX_DOMAIN_SHIFTS, OUTBOX_SNAPSHOT)
            val baseRevision = prior?.payload?.let { runCatching { JSONObject(it).getLong("baseRevision") }.getOrNull() }
                ?: db.syncRevisionDao().get(uid, profileId, OUTBOX_DOMAIN_SHIFTS, OUTBOX_SNAPSHOT)
                ?: 0L
            db.syncOutboxDao().upsert(
                SyncOutboxEntity(
                    UUID.randomUUID().toString(), uid, profileId, OUTBOX_DOMAIN_SHIFTS, OUTBOX_SNAPSHOT,
                    OUTBOX_SNAPSHOT, JSONObject().put("baseRevision", baseRevision).toString(), System.currentTimeMillis(),
                ),
            )
        }
        context?.let(::scheduleSyncOutbox)
    }

    suspend fun drainOutbox(limit: Int = 100): Boolean {
        var failed = false
        db.syncOutboxDao().oldestForDomain(OUTBOX_DOMAIN_SHIFTS, limit).forEach { operation ->
            runCatching { uploadFullSnapshot(operation) }
                .onSuccess { revision ->
                    db.syncRevisionDao().upsert(SyncRevisionEntity(operation.ownerUid, operation.profileId, OUTBOX_DOMAIN_SHIFTS, OUTBOX_SNAPSHOT, revision))
                    db.syncOutboxDao().delete(operation.operationId)
                }
                .onFailure { error ->
                    failed = true
                    val failure = if (error.snapshotConflict()) SyncFailure(SyncFailure.Kind.CONFLICT, false, "SYNC_CONFLICT") else SyncFailure.from(error)
                    SafeDiagnostics.reportSync(SafeDiagnostics.Domain.SHIFTS, failure)
                    db.syncOutboxDao().markFailed(operation.operationId, failure.diagnosticCode)
                }
        }
        return !failed && db.syncOutboxDao().countForDomain(OUTBOX_DOMAIN_SHIFTS) == 0
    }

    private suspend fun uploadFullSnapshot(operation: SyncOutboxEntity): Long {
        val expected = operation.payload?.let { runCatching { JSONObject(it).getLong("baseRevision") }.getOrNull() }
            ?: db.syncRevisionDao().get(operation.ownerUid, operation.profileId, OUTBOX_DOMAIN_SHIFTS, OUTBOX_SNAPSHOT)
            ?: 0L
        val types = db.shiftTypeDao().getAllOnce(operation.ownerUid, operation.profileId).map { it.toRemoteMap() }
        val days = db.shiftDayDao().getAllOnce(operation.ownerUid, operation.profileId).groupBy({ it.dateKey }, { it.shiftTypeId })
        val schedule = db.autoFillScheduleDao().getOnce(operation.ownerUid, operation.profileId)
            ?: AutoFillScheduleEntity(0, false, "", "every", "", operation.ownerUid, operation.profileId)
        val ref = shiftsDocRef(operation.ownerUid, operation.profileId)
        return firestore.runTransaction { transaction ->
            val actual = transaction.get(ref).getLong("revision") ?: 0L
            if (actual != expected) throw SnapshotConflictException()
            transaction.set(
                ref,
                mapOf(
                    "shiftTypes" to types, "data" to days, "autoFillSchedule" to schedule.toRemoteMap(),
                    "revision" to expected + 1, "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            )
            expected + 1
        }.await()
    }

    private suspend fun hasPending(uid: String, profileId: String) =
        db.syncOutboxDao().get(uid, profileId, OUTBOX_DOMAIN_SHIFTS).isNotEmpty()

    private suspend fun rememberRevision(uid: String, profileId: String, revision: Long) {
        db.syncRevisionDao().upsert(SyncRevisionEntity(uid, profileId, OUTBOX_DOMAIN_SHIFTS, OUTBOX_SNAPSHOT, revision))
    }
}

internal const val OUTBOX_DOMAIN_SHIFTS = "shifts"
private const val OUTBOX_SNAPSHOT = "snapshot"

private fun AutoFillScheduleEntity.toRemoteMap(): Map<String, Any> = mapOf(
    "enabled" to enabled, "typeId" to typeId, "pattern" to pattern, "anchorDate" to anchorDate,
)

private fun ShiftTypeEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "short" to short,
    "code" to code,
    "color" to colorHexToWebString(colorHex),
    "amount" to amount,
    "hours" to hours,
    "isOff" to isOff,
)

private fun parseRemoteShiftType(m: Map<*, *>): ShiftTypeEntity? {
    val id = m["id"] as? String ?: return null
    val name = m["name"] as? String ?: return null
    val short = m["short"] as? String ?: name.take(4)
    val code = m["code"] as? String ?: ""
    val amount = (m["amount"] as? Number)?.toDouble() ?: 0.0
    val hours = (m["hours"] as? Number)?.toDouble() ?: 0.0
    val isOff = m["isOff"] as? Boolean ?: false
    return ShiftTypeEntity(
        id = id, name = name, short = short, code = code,
        colorHex = webStringToColorHex(m["color"] as? String),
        amount = amount, hours = hours, isOff = isOff,
    )
}
