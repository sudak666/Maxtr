package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.CategoryEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.SubcategoryEntity
import java.util.UUID

// Same one-time cold-sync bootstrap pattern as FinanceSyncRepository (wallets)/
// ShiftsSyncRepository (shift types), applied to the `finance` doc's `categories`
// field — see ANDROID_MIGRATION.md's step for exactly what's in/out of scope.
//
// Unlike wallets/shift types, the PWA's `categories` field has NO id concept at
// all — it's just `{income: string[], expense: string[]}` (js/state.js's
// AppState.categories). CategoryEntity's own `id` field is purely a local Room
// bookkeeping detail (a random UUID, used only so the manager screen can target
// a row for rename/delete — see FinanceRepository.addCategory()); it has no
// remote counterpart and is never written to Firestore. Identity for sync
// purposes is (type, name) — the same pair the PWA itself uses to dedupe.
//
// Uses SetOptions.merge(true) and only ever touches the `categories`/`updatedAt`
// keys — never a full-doc setDoc(..., {merge:false}) — so this can never wipe
// out subcategories/categoryIcons/budgets/tags/etc., none of which Android has
// Room models for yet (chesno not done, same disclosed scope as wallets/shift
// types before it).
class CategoriesSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncCategoriesOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteCategories = snapshot.get("categories") as? Map<*, *>
        if (snapshot.exists() && remoteCategories != null) {
            // Remote wins on cold sign-in — same bootstrap direction as wallets/shift types.
            val entities = mutableListOf<CategoryEntity>()
            (remoteCategories["income"] as? List<*>)?.forEach { name ->
                (name as? String)?.let { entities += CategoryEntity(id = UUID.randomUUID().toString(), type = "INCOME", name = it) }
            }
            (remoteCategories["expense"] as? List<*>)?.forEach { name ->
                (name as? String)?.let { entities += CategoryEntity(id = UUID.randomUUID().toString(), type = "EXPENSE", name = it) }
            }
            db.categoryDao().replaceAll(entities)
        } else {
            // First-time account (no finance doc yet, or one predating categories
            // syncing) — push this device's local categories up as the seed.
            val local = db.categoryDao().getAllOnce()
            val remoteMap = mapOf(
                "income" to local.filter { it.type == "INCOME" }.map { it.name },
                "expense" to local.filter { it.type == "EXPENSE" }.map { it.name },
            )
            docRef.set(mapOf("categories" to remoteMap, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        }
    }

    // Second slice of the `finance` doc's category-adjacent state: `subcategories`
    // (js/state.js's AppState.subcategories, `Record<subKey(type,name), string[]>`
    // — js/core.js's subKey(type,name) => `${type}:${name}`, with `type` the
    // PWA's own lowercase 'income'/'expense' string, NOT Android's uppercase
    // TxType.name). This lower/upper-case translation is the one real gotcha
    // here — get it wrong and every subcategory silently fails to round-trip
    // with a real PWA account even though it round-trips fine
    // Android-to-Android. Same SetOptions.merge() safety rule, touching only
    // `subcategories`/`updatedAt`.
    suspend fun syncSubcategoriesOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteSubs = snapshot.get("subcategories") as? Map<*, *>
        if (snapshot.exists() && remoteSubs != null) {
            val entities = mutableListOf<SubcategoryEntity>()
            remoteSubs.forEach { (key, names) ->
                val parts = (key as? String)?.split(":", limit = 2) ?: return@forEach
                if (parts.size != 2) return@forEach
                val type = parts[0].uppercase()
                val categoryName = parts[1]
                (names as? List<*>)?.forEach { name ->
                    (name as? String)?.let { entities += SubcategoryEntity(categoryType = type, categoryName = categoryName, name = it) }
                }
            }
            db.subcategoryDao().replaceAll(entities)
        } else {
            val local = db.subcategoryDao().getAllOnce()
            val remoteMap = local.groupBy({ "${it.categoryType.lowercase()}:${it.categoryName}" }, { it.name })
            docRef.set(mapOf("subcategories" to remoteMap, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        }
    }
}
