package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.WalletEntity

// Mirrors js/color-picker.js's fbSaveNow()/js/firebase-sync.js's fbLoadNow() for
// the `finance` doc's `wallets` field ONLY — see ANDROID_MIGRATION.md's step-14
// section for exactly what's in/out of scope (categories are synced separately,
// see CategoriesSyncRepository; subcategories/categoryIcons/budgets/tags/etc.
// are NOT synced yet — chesno not done). This is a one-time COLD sync triggered on
// sign-in, not continuous two-way sync (no debounced writes on every local
// edit, no snapshot listener) — that's a bigger future step.
//
// Uses SetOptions.merge(true) and only ever touches the `wallets`/`updatedAt`
// keys — never a full-doc setDoc(..., {merge:false}) — so this can NEVER wipe
// out the many other fields the PWA's finance doc already carries (categories,
// budgets, tags, recurring, goals, ...), even though Android doesn't have Room
// models for any of those yet. A field this doesn't know about is left
// completely alone on the remote doc. `color` is written/read as the PWA's own
// "#rrggbb" hex string, not Android's internal ARGB Long, so a wallet created
// on either platform renders correctly on the other.
class FinanceSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {

    private fun financeDocRef(uid: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document("finance")

    suspend fun syncWalletsOnSignIn(uid: String) {
        val docRef = financeDocRef(uid)
        val snapshot = docRef.get().await()
        val remoteWallets = snapshot.get("wallets") as? List<*>
        if (snapshot.exists() && remoteWallets != null) {
            // Remote wins on cold sign-in — same bootstrap direction as the PWA's
            // fbLoadNow() (load-then-render), just without the continuous sync after.
            val entities = remoteWallets.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteWallet) }
            db.walletDao().replaceAll(entities)
        } else {
            // First-time account (no finance doc yet, or one predating wallets
            // syncing at all) — push this device's local wallets up as the seed.
            val local = db.walletDao().getAllOnce()
            docRef.set(
                mapOf("wallets" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }
}

private fun WalletEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "color" to colorHexToWebString(colorHex),
    "icon" to icon,
    "currency" to currency,
)

private fun parseRemoteWallet(m: Map<*, *>): WalletEntity? {
    val id = m["id"] as? String ?: return null
    val name = m["name"] as? String ?: return null
    val icon = m["icon"] as? String ?: "card"
    val currency = m["currency"] as? String ?: "UAH"
    return WalletEntity(id = id, name = name, colorHex = webStringToColorHex(m["color"] as? String), currency = currency, icon = icon)
}
