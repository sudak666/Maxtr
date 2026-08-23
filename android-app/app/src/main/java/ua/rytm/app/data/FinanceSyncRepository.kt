package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.WalletEntity

// Wallet snapshots share the finance document with independently synchronized
// fields. Colors cross the wire as PWA-compatible `#rrggbb` strings.
class FinanceSyncRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
    private val outbox: FinanceSnapshotOutboxRepository = FinanceSnapshotOutboxRepository(db, firestore),
) {

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun syncWalletsOnSignIn(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val docRef = financeDocRef(uid, profileId)
        val snapshot = docRef.get().await()
        val remoteWallets = snapshot.get("wallets") as? List<*>
        outbox.rememberRemoteRevision(uid, profileId, "wallets", snapshot.getLong("fieldRevisions.wallets") ?: 0L)
        if (snapshot.exists() && remoteWallets != null && outbox.hasPending(uid, profileId, "wallets")) return
        if (snapshot.exists() && remoteWallets != null && !outbox.hasPending(uid, profileId, "wallets")) {
            // Remote wins on cold sign-in — same bootstrap direction as the PWA's
            // fbLoadNow() (load-then-render), just without the continuous sync after.
            val entities = remoteWallets.mapNotNull { (it as? Map<*, *>)?.let(::parseRemoteWallet) }
                .map { it.copy(ownerUid = uid, profileId = profileId) }
            db.walletDao().replaceAll(entities, uid, profileId)
        } else {
            // First-time account (no finance doc yet, or one predating wallets
            // syncing at all) — push this device's local wallets up as the seed.
            val local = db.walletDao().getAllOnce(uid, profileId)
            docRef.set(
                mapOf("wallets" to local.map { it.toRemoteMap() }, "updatedAt" to System.currentTimeMillis()),
                SetOptions.merge(),
            ).await()
        }
    }

    /** Serializes Room snapshots so an older write can never overtake a newer wallet edit. */
    suspend fun saveWalletsSnapshot(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val wallets = db.walletDao().getAllOnce(uid, profileId)
        outbox.queue(uid, profileId, "wallets", wallets.map { it.toRemoteMap() })
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
