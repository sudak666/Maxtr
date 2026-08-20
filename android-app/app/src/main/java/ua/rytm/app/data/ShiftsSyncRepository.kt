package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
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

    private fun shiftsDocRef(uid: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document("shifts")

    suspend fun syncShiftTypesOnSignIn(uid: String) {
        val docRef = shiftsDocRef(uid)
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
    // autoFillSchedule itself stays unsynced (chesno not done) — Android's
    // Shifts screen never implemented quick-fill/autofill at all (Step 8's
    // disclosed scope), so there's no local Room field to round-trip yet.
    suspend fun syncShiftDaysOnSignIn(uid: String) {
        val docRef = shiftsDocRef(uid)
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
}

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
