package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.TagEntity

// Same one-time cold-sync bootstrap pattern as every other synced field on
// the `finance` doc, applied to `tags` (js/state.js's AppState.tags,
// `[{id,name,color}]` — confirmed by reading js/finance.js's addTag()).
// `color` round-trips as the PWA's own "#rrggbb" hex string, same convention
// as FinanceSyncRepository's wallets. Uses SetOptions.merge() touching only
// `tags`/`updatedAt`.
class TagsSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {

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
