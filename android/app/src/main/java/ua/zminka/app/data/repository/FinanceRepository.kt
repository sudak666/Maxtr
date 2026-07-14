package ua.zminka.app.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import ua.zminka.app.data.model.FinanceDoc
import ua.zminka.app.data.model.ShoppingItem
import ua.zminka.app.data.model.Transaction
import ua.zminka.app.data.profile.ProfileManager

/**
 * Mirrors js/firebase-sync.js's userDoc()/txCollection() path scheme:
 * users/{uid}/max_tracker/{doc}, with a `transactions` subcollection
 * nested under the `finance` doc (see CLAUDE.md's Firebase data model
 * section — a direct migration cutover, not the old finance.data array).
 *
 * Doc names are suffixed per the currently active profile via
 * [ProfileManager.suffixedDocName] — the same "@<profileId>" scheme
 * userDoc() uses (see CLAUDE.md's "Multiple profiles per account"
 * section). `observeFinanceDoc()`/`observeTransactions()` wrap their
 * snapshot listeners in `ProfileManager.activeProfileId.flatMapLatest`
 * so switching profiles live-resubscribes to the new profile's doc path
 * instead of requiring the screen to be recreated.
 */
class FinanceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private companion object {
        const val DCOL = "max_tracker"
        const val FINANCE_DOC = "finance"
    }

    private fun userDoc(uid: String, name: String): DocumentReference =
        db.collection("users").document(uid).collection(DCOL).document(ProfileManager.suffixedDocName(name))

    private fun financeDocRef(uid: String): DocumentReference = userDoc(uid, FINANCE_DOC)

    private fun txCollection(uid: String): CollectionReference =
        financeDocRef(uid).collection("transactions")

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFinanceDoc(uid: String): Flow<FinanceDoc> = ProfileManager.activeProfileId.flatMapLatest {
        callbackFlow {
            val reg = financeDocRef(uid).addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(FinanceDoc::class.java) ?: FinanceDoc())
            }
            awaitClose { reg.remove() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTransactions(uid: String): Flow<List<Transaction>> = ProfileManager.activeProfileId.flatMapLatest {
        callbackFlow {
            val reg = txCollection(uid).addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc -> doc.toObject(Transaction::class.java) }
                    ?.sortedByDescending { tx -> tx.date + tx.id }
                    ?: emptyList()
                trySend(list)
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun saveTransaction(uid: String, tx: Transaction) {
        // Full overwrite, not merge — matches js/firebase-sync.js's
        // saveTransactionDoc() ({merge:false}), so an edited transaction's
        // old field values can't linger if a field is ever removed.
        txCollection(uid).document(tx.id.toString()).set(tx).await()
    }

    suspend fun deleteTransaction(uid: String, id: Long) {
        txCollection(uid).document(id.toString()).delete().await()
    }

    /**
     * Field-scoped update — mirrors js/shopping.js's per-action calls
     * (addShoppingItem()/toggleShoppingItem()/deleteShoppingItem()/
     * clearBoughtShopping()), which all mutate AppState.shoppingList then
     * go through the same scheduleSave() as everything else in the
     * `finance` doc. This client updates just the one field instead of
     * round-tripping the whole FinanceDoc, since none of this app's
     * shopping-list callers have (or need) the rest of the doc's fields
     * in hand.
     */
    suspend fun saveShoppingList(uid: String, items: List<ShoppingItem>) {
        financeDocRef(uid).update(mapOf("shoppingList" to items, "updatedAt" to System.currentTimeMillis())).await()
    }

    /** Wallet/category balances, mirroring js/analytics-csv.js's computeWalletBalances(). */
    fun walletBalances(transactions: List<Transaction>): Map<String, Double> {
        val balances = mutableMapOf<String, Double>()
        transactions.forEach { t ->
            when (t.type) {
                "income" -> balances[t.wallet] = (balances[t.wallet] ?: 0.0) + t.amount
                "expense" -> balances[t.wallet] = (balances[t.wallet] ?: 0.0) - t.amount
                "transfer" -> {
                    balances[t.wallet] = (balances[t.wallet] ?: 0.0) - t.amount
                    val target = t.targetWallet
                    if (target != null) {
                        val credited = t.targetAmount ?: t.amount
                        balances[target] = (balances[target] ?: 0.0) + credited
                    }
                }
            }
        }
        return balances
    }
}
