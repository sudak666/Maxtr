package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.BudgetEntity
import ua.rytm.app.data.local.RytmDatabase

// Same one-time cold-sync bootstrap pattern as CategoriesSyncRepository/
// ShoppingSyncRepository, applied to the `finance` doc's `budgets` field
// (js/state.js's AppState.budgets, `Record<expenseCategoryName, number>` —
// confirmed by reading js/settings-managers.js's updateBudget(), no type
// prefix needed since only EXPENSE categories can have a budget on either
// platform — see BudgetEntity's own doc comment). Uses SetOptions.merge()
// touching only `budgets`/`updatedAt`, same safety rule as every other
// synced field on the shared `finance` doc.
class BudgetsSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val outbox: FinanceSnapshotOutboxRepository = FinanceSnapshotOutboxRepository(db, firestore),
) {

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncBudgetsOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteBudgets = snapshot.get("budgets") as? Map<*, *>
        outbox.rememberRemoteRevision(uid, profileId, "budgets", snapshot.getLong("fieldRevisions.budgets") ?: 0L)
        if (snapshot.exists() && remoteBudgets != null && outbox.hasPending(uid, profileId, "budgets")) return
        if (snapshot.exists() && remoteBudgets != null && !outbox.hasPending(uid, profileId, "budgets")) {
            // Remote wins on cold sign-in — same bootstrap direction as every other synced domain.
            val entities = remoteBudgets.mapNotNull { (category, amount) ->
                val name = category as? String ?: return@mapNotNull null
                val value = (amount as? Number)?.toDouble() ?: return@mapNotNull null
                if (value > 0) BudgetEntity(category = name, amount = value, ownerUid = uid, profileId = profileId) else null
            }
            db.budgetDao().replaceAll(entities, uid, profileId)
        } else {
            // First-time account (no finance doc yet, or one predating budgets syncing) —
            // push this device's local budgets up as the seed.
            val local = db.budgetDao().getAllOnce(uid, profileId)
            val remoteMap = local.associate { it.category to it.amount }
            docRef.set(mapOf("budgets" to remoteMap, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        }
    }

    suspend fun saveBudgetsSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val budgets = db.budgetDao().getAllOnce(uid, profileId).associate { it.category to it.amount }
        outbox.queue(uid, profileId, "budgets", budgets)
    }
}
