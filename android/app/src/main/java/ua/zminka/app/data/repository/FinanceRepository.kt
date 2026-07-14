package ua.zminka.app.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ua.zminka.app.data.model.FinanceDoc
import ua.zminka.app.data.model.Transaction

/**
 * Mirrors js/firebase-sync.js's userDoc()/txCollection() path scheme:
 * users/{uid}/max_tracker/{doc}, with a `transactions` subcollection
 * nested under the `finance` doc (see CLAUDE.md's Firebase data model
 * section — a direct migration cutover, not the old finance.data array).
 *
 * MVP scope: default profile only (activeProfileId == 'default', i.e. no
 * "@<profileId>" doc-name suffix — see the web client's Multiple profiles
 * section). Multi-profile support can be added later by threading a
 * profileId through these path builders the same way userDoc() does.
 */
class FinanceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private companion object {
        const val DCOL = "max_tracker"
        const val FINANCE_DOC = "finance"
    }

    private fun userDoc(uid: String, name: String): DocumentReference =
        db.collection("users").document(uid).collection(DCOL).document(name)

    private fun financeDocRef(uid: String): DocumentReference = userDoc(uid, FINANCE_DOC)

    private fun txCollection(uid: String): CollectionReference =
        financeDocRef(uid).collection("transactions")

    fun observeFinanceDoc(uid: String): Flow<FinanceDoc> = callbackFlow {
        val reg = financeDocRef(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(FinanceDoc::class.java) ?: FinanceDoc())
        }
        awaitClose { reg.remove() }
    }

    fun observeTransactions(uid: String): Flow<List<Transaction>> = callbackFlow {
        val reg = txCollection(uid).addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { it.toObject(Transaction::class.java) }
                ?.sortedByDescending { it.date + it.id }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
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
