package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.rytm.app.data.local.RecurringEntity
import ua.rytm.app.data.local.RytmDatabase

// Same one-time cold-sync bootstrap pattern as every other synced field on the
// `finance` doc, applied to `recurring` (js/state.js's AppState.recurring,
// `[{id,type,amount,category,wallet,frequency,nextDate,active,comment}]` —
// confirmed by reading js/settings-managers.js's addRecurring()/
// updateRecurring() and js/color-picker.js's processRecurring()). `type`
// round-trips lowercase ("income"/"expense") on the wire, same
// upper-on-device/lower-on-wire translation CategoriesSyncRepository already
// established for `categories`/`subcategories` — RecurringEntity itself stores
// TxType.name (uppercase), see that entity's own doc comment. Uses
// SetOptions.merge() touching only `recurring`/`updatedAt`.
class RecurringSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {

    private val saveMutex = Mutex()

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncRecurringOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteRecurring = snapshot.get("recurring") as? List<*>
        if (snapshot.exists() && remoteRecurring != null) {
            val entities = remoteRecurring.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteRecurring) }
                .map { it.copy(ownerUid = uid, profileId = profileId) }
            db.recurringDao().replaceAll(entities, uid, profileId)
        } else {
            val local = db.recurringDao().getAllOnce(uid, profileId)
            docRef.set(
                mapOf("recurring" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    suspend fun saveRecurringSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val recurring = db.recurringDao().getAllOnce(uid, profileId)
        financeDocRef(uid, profileId).set(
            mapOf("recurring" to recurring.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        ).await()
    }
}

private fun RecurringEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "type" to type.lowercase(),
    "amount" to amount,
    "category" to category,
    "wallet" to walletId,
    "frequency" to frequency,
    "nextDate" to nextDate,
    "active" to active,
    "comment" to comment,
)

private fun parseRemoteRecurring(m: Map<*, *>): RecurringEntity? {
    val id = m["id"] as? String ?: return null
    // Only INCOME/EXPENSE are valid — the PWA's own recurring-modal type <select>
    // never offers "transfer", but a malformed/manually-edited remote doc
    // shouldn't crash TxType.valueOf() downstream at read time.
    val type = (m["type"] as? String)?.uppercase()?.takeIf { it == "INCOME" || it == "EXPENSE" } ?: return null
    val category = m["category"] as? String ?: return null
    val walletId = m["wallet"] as? String ?: return null
    val nextDate = m["nextDate"] as? String ?: return null
    return RecurringEntity(
        id = id,
        type = type,
        amount = (m["amount"] as? Number)?.toDouble() ?: 0.0,
        category = category,
        walletId = walletId,
        frequency = m["frequency"] as? String ?: "monthly",
        nextDate = nextDate,
        active = m["active"] as? Boolean ?: true,
        comment = m["comment"] as? String ?: "",
    )
}
