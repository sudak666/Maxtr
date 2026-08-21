package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.ShoppingItemEntity

// Same one-time cold-sync bootstrap pattern as Categories/Finance/ShiftsSyncRepository,
// applied to the `finance` doc's `shoppingList` field (js/state.js's
// AppState.shoppingList, `{id,name,qty,done,createdAt}` — confirmed against
// js/shopping.js's addShoppingItem(), a direct 1:1 with ShoppingItemEntity, no
// field-mapping decisions needed unlike wallets/transactions).
//
// Uses SetOptions.merge(true) and only ever touches the `shoppingList`/`updatedAt`
// keys — never a full-doc setDoc(..., {merge:false}) — same safety rule as every
// other synced field on the shared `finance` doc.
class ShoppingSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncShoppingListOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteList = snapshot.get("shoppingList") as? List<*>
        if (snapshot.exists() && remoteList != null) {
            // Remote wins on cold sign-in — same bootstrap direction as every other synced domain.
            val entities = remoteList.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteItem) }
            db.shoppingDao().replaceAll(entities)
        } else {
            // First-time account (no finance doc yet, or one predating shoppingList
            // syncing) — push this device's local list up as the seed.
            val local = db.shoppingDao().getAllOnce()
            docRef.set(
                mapOf("shoppingList" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }
}

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
