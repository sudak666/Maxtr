package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.rytm.app.data.local.AutoFillScheduleEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.ShiftDayEntity
import ua.rytm.app.data.local.ShiftTypeEntity

// Second Firestore sync slice (see FinanceSyncRepository for the first,
// wallets) — same one-time cold-sync-on-sign-in shape, same safety rule:
// SetOptions.merge() touching ONLY the `shiftTypes`/`updatedAt` keys of the
// `shifts` doc, never a full-doc replace. The PWA's `shifts` doc also carries
// `data` (the actual calendar day→shiftType assignments) and
// `autoFillSchedule` — neither is touched here, so this can never wipe a
// user's calendar. Mirrors js/color-picker.js's fbSaveNow() write shape:
// setDoc(userDoc('shifts'), {data, shiftTypes, autoFillSchedule, updatedAt}).
class ShiftsSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {
    private val saveMutex = Mutex()

    private fun shiftsDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("shifts", profileId))

    suspend fun syncShiftTypesOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = shiftsDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteTypes = snapshot.get("shiftTypes") as? List<*>
        if (snapshot.exists() && remoteTypes != null) {
            val entities = remoteTypes.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteShiftType) }
            db.shiftTypeDao().replaceAll(entities)
        } else {
            val local = db.shiftTypeDao().getAllOnce()
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
        val docRef = shiftsDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteData = snapshot.get("data") as? Map<*, *>
        if (snapshot.exists() && remoteData != null) {
            val entities = mutableListOf<ShiftDayEntity>()
            remoteData.forEach { (dateKey, ids) ->
                val key = dateKey as? String ?: return@forEach
                (ids as? List<*>)?.forEach { id -> (id as? String)?.let { entities += ShiftDayEntity(key, it) } }
            }
            db.shiftDayDao().replaceAll(entities)
        } else {
            val local = db.shiftDayDao().getAllOnce()
            val remoteMap = local.groupBy({ it.dateKey }, { it.shiftTypeId })
            docRef.set(
                mapOf("data" to remoteMap, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    // Fourth and final slice of the `shifts` doc — autoFillSchedule (step 39
    // gave it a real Room row, see AutoFillScheduleEntity). Same
    // SetOptions.merge() safety rule, touching only `autoFillSchedule`/
    // `updatedAt` — never `data`/`shiftTypes`. Unlike the other two slices,
    // "remote has no autoFillSchedule at all" (a pre-step-39 doc) is treated
    // the same as "remote disabled" — push the local default (disabled)
    // rather than leaving Room empty, mirroring js/state.js's own
    // always-present default object.
    suspend fun syncAutoFillScheduleOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = shiftsDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remote = snapshot.get("autoFillSchedule") as? Map<*, *>
        if (snapshot.exists() && remote != null) {
            db.autoFillScheduleDao().upsert(
                AutoFillScheduleEntity(
                    id = 0,
                    enabled = remote["enabled"] as? Boolean ?: false,
                    typeId = remote["typeId"] as? String ?: "",
                    pattern = remote["pattern"] as? String ?: "every",
                    anchorDate = remote["anchorDate"] as? String ?: "",
                ),
            )
        } else {
            val local = db.autoFillScheduleDao().getOnce() ?: AutoFillScheduleEntity(id = 0, enabled = false, typeId = "", pattern = "every", anchorDate = "")
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

    suspend fun saveShiftTypes(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val types = db.shiftTypeDao().getAllOnce().map { it.toRemoteMap() }
        shiftsDocRef(uid, profileId).set(
            mapOf("shiftTypes" to types, "updatedAt" to System.currentTimeMillis()), SetOptions.merge(),
        ).await()
    }

    suspend fun saveShiftDays(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        saveShiftDaysLocked(uid, profileId)
    }

    suspend fun saveAutoFillSchedule(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val schedule = db.autoFillScheduleDao().getOnce() ?: AutoFillScheduleEntity(0, false, "", "every", "")
        shiftsDocRef(uid, profileId).set(
            mapOf("autoFillSchedule" to schedule.toRemoteMap(), "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun saveShiftTypesAndDays(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val types = db.shiftTypeDao().getAllOnce().map { it.toRemoteMap() }
        val days = db.shiftDayDao().getAllOnce().groupBy({ it.dateKey }, { it.shiftTypeId })
        shiftsDocRef(uid, profileId).set(
            mapOf("shiftTypes" to types, "data" to days, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun saveAutoFillAndDays(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val schedule = db.autoFillScheduleDao().getOnce() ?: AutoFillScheduleEntity(0, false, "", "every", "")
        val days = db.shiftDayDao().getAllOnce().groupBy({ it.dateKey }, { it.shiftTypeId })
        shiftsDocRef(uid, profileId).set(
            mapOf("autoFillSchedule" to schedule.toRemoteMap(), "data" to days, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        ).await()
    }

    private suspend fun saveShiftDaysLocked(uid: String, profileId: String) {
        val days = db.shiftDayDao().getAllOnce().groupBy({ it.dateKey }, { it.shiftTypeId })
        shiftsDocRef(uid, profileId).set(
            mapOf("data" to days, "updatedAt" to System.currentTimeMillis()), SetOptions.merge(),
        ).await()
    }
}

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
