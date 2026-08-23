package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.GoalEntity
import ua.rytm.app.data.local.RytmDatabase

// Same one-time cold-sync bootstrap pattern as recurring/budgets, applied to
// `goals` (js/state.js's AppState.goals, `[{id,walletId,targetAmount,
// targetDate}]` — confirmed by reading js/goals-profile.js's
// confirmAddGoal()). Field names round-trip as-is, no upper/lowercase
// translation needed (unlike recurring's type field).
class GoalsSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val outbox: FinanceSnapshotOutboxRepository = FinanceSnapshotOutboxRepository(db, firestore),
) {

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncGoalsOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteGoals = snapshot.get("goals") as? List<*>
        outbox.rememberRemoteRevision(uid, profileId, "goals", snapshot.getLong("fieldRevisions.goals") ?: 0L)
        if (snapshot.exists() && remoteGoals != null && outbox.hasPending(uid, profileId, "goals")) return
        if (snapshot.exists() && remoteGoals != null && !outbox.hasPending(uid, profileId, "goals")) {
            val entities = remoteGoals.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteGoal) }
                .map { it.copy(ownerUid = uid, profileId = profileId) }
            db.goalDao().replaceAll(entities, uid, profileId)
        } else {
            val local = db.goalDao().getAllOnce(uid, profileId)
            docRef.set(
                mapOf("goals" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    suspend fun saveGoalsSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val goals = db.goalDao().getAllOnce(uid, profileId)
        outbox.queue(uid, profileId, "goals", goals.map { it.toRemoteMap() })
    }
}

private fun GoalEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "walletId" to walletId,
    "targetAmount" to targetAmount,
    "targetDate" to targetDate,
)

private fun parseRemoteGoal(m: Map<*, *>): GoalEntity? {
    val id = m["id"] as? String ?: return null
    val walletId = m["walletId"] as? String ?: return null
    return GoalEntity(
        id = id,
        walletId = walletId,
        targetAmount = (m["targetAmount"] as? Number)?.toDouble() ?: 0.0,
        targetDate = m["targetDate"] as? String ?: "",
    )
}
