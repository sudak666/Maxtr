package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.TagEntity
import ua.rytm.app.data.local.TransactionEntity

// Same one-time cold-sync bootstrap pattern as every other synced field on
// the `finance` doc, applied to `tags` (js/state.js's AppState.tags,
// `[{id,name,color}]` — confirmed by reading js/finance.js's addTag()).
// `color` round-trips as the PWA's own "#rrggbb" hex string, same convention
// as FinanceSyncRepository's wallets. Uses SetOptions.merge() touching only
// `tags`/`updatedAt`.
class TagsSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {
    private val saveMutex = Mutex()

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncTagsOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteTags = snapshot.get("tags") as? List<*>
        if (snapshot.exists() && remoteTags != null) {
            val entities = remoteTags.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteTag) }
            db.tagDao().replaceAll(entities)
        } else {
            val local = db.tagDao().getAllOnce()
            docRef.set(
                mapOf("tags" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    suspend fun saveTagsSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val tags = db.tagDao().getAllOnce().map { it.toRemoteMap() }
        financeDocRef(uid, profileId).set(
            mapOf("tags" to tags, "updatedAt" to System.currentTimeMillis()), SetOptions.merge(),
        ).await()
    }

    suspend fun saveTagsAndChangedTransactions(
        uid: String,
        profileId: String = DEFAULT_PROFILE_ID,
        changedTransactions: List<TransactionEntity>,
    ) = saveMutex.withLock {
        val batch = firestore.batch()
        val financeRef = financeDocRef(uid, profileId)
        val tags = db.tagDao().getAllOnce().map { it.toRemoteMap() }
        batch.set(financeRef, mapOf("tags" to tags, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
        val transactions = financeRef.collection("transactions")
        changedTransactions.forEach { tx -> batch.set(transactions.document(tx.id), tx.toRemoteMap()) }
        require(changedTransactions.size <= 499) { "Too many tagged transactions for one atomic update" }
        batch.commit().await()
    }
}

private fun TagEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "color" to colorHexToWebString(colorHex),
)

private fun parseRemoteTag(m: Map<*, *>): TagEntity? {
    val id = m["id"] as? String ?: return null
    val name = m["name"] as? String ?: return null
    return TagEntity(id = id, name = name, colorHex = webStringToColorHex(m["color"] as? String))
}
