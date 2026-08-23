package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.AutoRuleEntity
import ua.rytm.app.data.local.RytmDatabase

class AutoRulesSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val outbox: FinanceSnapshotOutboxRepository = FinanceSnapshotOutboxRepository(db, firestore),
) {
    private fun ref(uid: String, profileId: String) = firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))
    suspend fun syncOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val ref = ref(uid, profileId)
        val snapshot = ref.get().await()
        val remote = snapshot.get("autoRules") as? List<*>
        outbox.rememberRemoteRevision(uid, profileId, "autoRules", snapshot.getLong("fieldRevisions.autoRules") ?: 0L)
        if (snapshot.exists() && remote != null && outbox.hasPending(uid, profileId, "autoRules")) return
        if (snapshot.exists() && remote != null && !outbox.hasPending(uid, profileId, "autoRules")) db.autoRuleDao().replaceAll(remote.mapIndexedNotNull { index, item ->
            val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
            AutoRuleEntity(map["id"] as? String ?: return@mapIndexedNotNull null, map["type"] as? String ?: "expense", map["keyword"] as? String ?: "", map["category"] as? String ?: "", index).copy(ownerUid = uid, profileId = profileId)
        }, uid, profileId) else save(uid, profileId)
    }
    suspend fun save(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val rules = db.autoRuleDao().getAllOnce(uid, profileId).map { mapOf("id" to it.id, "type" to it.type.lowercase(), "keyword" to it.keyword, "category" to it.category) }
        outbox.queue(uid, profileId, "autoRules", rules)
    }
}
