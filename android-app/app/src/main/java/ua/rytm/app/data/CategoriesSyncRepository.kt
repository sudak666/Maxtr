package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.rytm.app.data.local.CategoryEntity
import ua.rytm.app.data.local.CategoryIconEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.SubcategoryEntity
import java.util.UUID

// Remote categories have no id; sync identity is the stable (type, name) pair.
// Local UUIDs exist only for Room row targeting and never leave the device.
class CategoriesSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val outbox: FinanceSnapshotOutboxRepository = FinanceSnapshotOutboxRepository(db, firestore),
    private val transactionsOutbox: TransactionsSyncRepository = TransactionsSyncRepository(db, firestore),
) {

    private val saveMutex = Mutex()

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncCategoriesOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteCategories = snapshot.get("categories") as? Map<*, *>
        outbox.rememberRemoteRevision(uid, profileId, "categories", snapshot.getLong("fieldRevisions.categories") ?: 0L)
        if (snapshot.exists() && remoteCategories != null && outbox.hasPending(uid, profileId, "categories")) return
        if (snapshot.exists() && remoteCategories != null && !outbox.hasPending(uid, profileId, "categories")) {
            // Remote wins on cold sign-in — same bootstrap direction as wallets/shift types.
            val entities = mutableListOf<CategoryEntity>()
            (remoteCategories["income"] as? List<*>)?.forEach { name ->
                (name as? String)?.let { entities += CategoryEntity(id = UUID.randomUUID().toString(), type = "INCOME", name = it, ownerUid = uid, profileId = profileId) }
            }
            (remoteCategories["expense"] as? List<*>)?.forEach { name ->
                (name as? String)?.let { entities += CategoryEntity(id = UUID.randomUUID().toString(), type = "EXPENSE", name = it, ownerUid = uid, profileId = profileId) }
            }
            db.categoryDao().replaceAll(entities, uid, profileId)
        } else {
            // First-time account (no finance doc yet, or one predating categories
            // syncing) — push this device's local categories up as the seed.
            val local = db.categoryDao().getAllOnce(uid, profileId)
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
        outbox.rememberRemoteRevision(uid, profileId, "subcategories", snapshot.getLong("fieldRevisions.subcategories") ?: 0L)
        if (snapshot.exists() && remoteSubs != null && outbox.hasPending(uid, profileId, "subcategories")) return
        if (snapshot.exists() && remoteSubs != null && !outbox.hasPending(uid, profileId, "subcategories")) {
            val entities = mutableListOf<SubcategoryEntity>()
            remoteSubs.forEach { (key, names) ->
                val parts = (key as? String)?.split(":", limit = 2) ?: return@forEach
                if (parts.size != 2) return@forEach
                val type = parts[0].uppercase()
                val categoryName = parts[1]
                (names as? List<*>)?.forEach { name ->
                    (name as? String)?.let { entities += SubcategoryEntity(categoryType = type, categoryName = categoryName, name = it, ownerUid = uid, profileId = profileId) }
                }
            }
            db.subcategoryDao().replaceAll(entities, uid, profileId)
        } else {
            val local = db.subcategoryDao().getAllOnce(uid, profileId)
            val remoteMap = local.groupBy({ "${it.categoryType.lowercase()}:${it.categoryName}" }, { it.name })
            docRef.set(mapOf("subcategories" to remoteMap, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        }
    }

    // Third slice of the `finance` doc's category-adjacent state:
    // `categoryIcons` (js/state.js's AppState.categoryIcons, `Record<name,
    // iconName>` — confirmed by reading js/settings-managers.js's
    // selectCategoryIcon()). No type prefix, same as budgets — see
    // CategoryIconEntity's own doc comment for why a name-only key matches
    // the PWA's own (lack of) type-scoping here. Same SetOptions.merge()
    // safety rule, touching only `categoryIcons`/`updatedAt`.
    suspend fun syncCategoryIconsOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteIcons = snapshot.get("categoryIcons") as? Map<*, *>
        outbox.rememberRemoteRevision(uid, profileId, "categoryIcons", snapshot.getLong("fieldRevisions.categoryIcons") ?: 0L)
        if (snapshot.exists() && remoteIcons != null && outbox.hasPending(uid, profileId, "categoryIcons")) return
        if (snapshot.exists() && remoteIcons != null && !outbox.hasPending(uid, profileId, "categoryIcons")) {
            val entities = remoteIcons.mapNotNull { (name, icon) ->
                val categoryName = name as? String ?: return@mapNotNull null
                val iconName = icon as? String ?: return@mapNotNull null
                CategoryIconEntity(categoryName, iconName, uid, profileId)
            }
            db.categoryIconDao().replaceAll(entities, uid, profileId)
        } else {
            val local = db.categoryIconDao().getAllOnce(uid, profileId)
            val remoteMap = local.associate { it.categoryName to it.iconName }
            docRef.set(mapOf("categoryIcons" to remoteMap, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        }
    }

    suspend fun saveCategoriesSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val local = db.categoryDao().getAllOnce(uid, profileId)
        val remote = mapOf(
            "income" to local.filter { it.type == "INCOME" }.map { it.name },
            "expense" to local.filter { it.type == "EXPENSE" }.map { it.name },
        )
        outbox.queue(uid, profileId, "categories", remote)
    }

    suspend fun saveSubcategoriesSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val remote = db.subcategoryDao().getAllOnce(uid, profileId).groupBy({ "${it.categoryType.lowercase()}:${it.categoryName}" }, { it.name })
        outbox.queue(uid, profileId, "subcategories", remote)
    }

    suspend fun saveCategoryIconsSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val remote = db.categoryIconDao().getAllOnce(uid, profileId).associate { it.categoryName to it.iconName }
        outbox.queue(uid, profileId, "categoryIcons", remote)
    }

    suspend fun saveAllCategorySnapshots(uid: String, profileId: String = DEFAULT_PROFILE_ID) = saveMutex.withLock {
        val categories = db.categoryDao().getAllOnce(uid, profileId)
        val subcategories = db.subcategoryDao().getAllOnce(uid, profileId)
        val icons = db.categoryIconDao().getAllOnce(uid, profileId)
        outbox.queue(uid, profileId, "categories", mapOf(
            "income" to categories.filter { it.type == "INCOME" }.map { it.name },
            "expense" to categories.filter { it.type == "EXPENSE" }.map { it.name },
        ))
        outbox.queue(uid, profileId, "subcategories", subcategories.groupBy({ "${it.categoryType.lowercase()}:${it.categoryName}" }, { it.name }))
        outbox.queue(uid, profileId, "categoryIcons", icons.associate { it.categoryName to it.iconName })
        transactionsOutbox.queueSaves(uid, profileId, db.transactionDao().getAllOnce(uid, profileId))
    }
}
