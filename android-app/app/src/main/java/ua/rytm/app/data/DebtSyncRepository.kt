package ua.rytm.app.data

import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.DebtEntity
import ua.rytm.app.data.local.DebtEntryEntity
import ua.rytm.app.data.local.RytmDatabase

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
// `currentDebtId` is NOT synced — on Android it's pure in-memory ViewModel
// state (DebtViewModel.currentDebtId), never persisted locally even on this
// device, so there's nothing meaningful to round-trip yet (chesno not done,
// same disclosed-scope spirit as every previous sync step).
//
// Unlike the `finance` doc, `debt` has no other PWA-only fields to protect
// (its only two keys are `data`/`updatedAt`), so SetOptions.merge() here is a
// belt-and-suspenders consistency choice with the rest of this file's
// repositories, not a hard requirement the way it is for `finance`.
class DebtSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {

    private fun debtDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("debt", profileId))

    suspend fun syncDebtsOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = debtDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val data = snapshot.get("data") as? Map<*, *>
        val remoteDebts = data?.get("debts") as? List<*>
        if (snapshot.exists() && remoteDebts != null) {
            // Remote wins on cold sign-in — same bootstrap direction as every other synced domain.
            val debts = mutableListOf<DebtEntity>()
            val entries = mutableListOf<DebtEntryEntity>()
            remoteDebts.forEach { d ->
                val m = d as? Map<*, *> ?: return@forEach
                val debt = parseRemoteDebt(m) ?: return@forEach
                debts += debt
                (m["entries"] as? List<*>)?.forEach { e ->
                    (e as? Map<*, *>)?.let(::parseRemoteEntry)?.let { entries += it.copy(debtId = debt.id) }
                }
            }
            db.withTransaction {
                db.debtDao().clearAll()
                db.debtEntryDao().clearAll()
                db.debtDao().insertAll(debts)
                db.debtEntryDao().insertAll(entries)
            }
        } else {
            // First-time account (no debt doc yet) — push this device's local debts up as the seed.
            val localDebts = db.debtDao().getAllOnce()
            val localEntries = db.debtEntryDao().getAllOnce().groupBy { it.debtId }
            val remoteDebtsOut = localDebts.map { it.toRemoteMap(localEntries[it.id].orEmpty()) }
            docRef.set(
                mapOf("data" to mapOf("debts" to remoteDebtsOut, "currentDebtId" to null), "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }
}

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
        currency = m["currency"] as? String ?: "грн",
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
