package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.TransactionEntity

// Same one-time cold-sync bootstrap pattern as Finance/Shifts/CategoriesSyncRepository,
// applied to the `transactions` SUBCOLLECTION (js/firebase-sync.js's
// txCollection()/saveTransactionDoc()) rather than a single field on the `finance`
// doc — see CLAUDE.md's "Transactions live in their own subcollection" section for
// why the PWA itself made this split (1 MiB doc-size risk, whole-doc rewrite per save).
//
// Each transaction doc is written whole (PWA's saveTransactionDoc() itself uses
// setDoc(..., {merge:false}) — safe here because, unlike the shared `finance` doc,
// a transactions/{id} doc has no other-platform-only fields to protect; it's 1:1
// owned by this one feature on both platforms).
//
// Field-name mapping (PWA tx object, js/finance.js's addTransaction(), -> Android
// TransactionEntity): wallet->walletId, targetWallet->targetWalletId, tags (array
// of tag ids) -> comma-joined string (matches this file's existing simplification
// for locally-created transactions, see FinanceEntities.kt's own doc comment —
// Android has no Tag entity yet either).
class TransactionsSyncRepository(private val db: RytmDatabase, private val firestore: FirebaseFirestore) {

    private fun txCollectionRef(uid: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document("finance")
            .collection("transactions")

    suspend fun syncTransactionsOnSignIn(uid: String) {
        val colRef = txCollectionRef(uid)
        val snapshot = colRef.get().await()
        if (!snapshot.isEmpty) {
            // Remote wins on cold sign-in — same bootstrap direction as every other synced domain.
            val entities = snapshot.documents.mapNotNull { it.data?.let(::parseRemoteTransaction) }
            db.transactionDao().replaceAll(entities)
        } else {
            // First-time account (no transactions subcollection yet) — push this
            // device's local transactions up as the seed, chunked the same way
            // js/firebase-sync.js's batchWriteTransactions() is (Firestore batches
            // cap at 500 ops; well under that here).
            val local = db.transactionDao().getAllOnce()
            local.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { tx -> batch.set(colRef.document(tx.id), tx.toRemoteMap()) }
                batch.commit().await()
            }
        }
    }
}

private fun TransactionEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "createdAt" to createdAt,
    "type" to type.lowercase(),
    "amount" to amount,
    "currency" to currency,
    "category" to category,
    "subcategory" to subcategory,
    "tags" to if (tags.isBlank()) emptyList<String>() else tags.split(","),
    "wallet" to walletId,
    "targetWallet" to targetWalletId,
    "targetAmount" to targetAmount,
    "targetCurrency" to targetCurrency,
    "date" to date,
    "comment" to comment,
)

private fun parseRemoteTransaction(m: Map<String, Any?>): TransactionEntity? {
    val id = m["id"]?.toString() ?: return null
    val type = (m["type"] as? String)?.uppercase() ?: return null
    val amount = (m["amount"] as? Number)?.toDouble() ?: return null
    val date = m["date"] as? String ?: return null
    val wallet = m["wallet"] as? String ?: return null
    val tags = (m["tags"] as? List<*>)?.filterIsInstance<String>()?.joinToString(",") ?: ""
    return TransactionEntity(
        id = id,
        type = type,
        amount = amount,
        currency = m["currency"] as? String ?: "UAH",
        date = date,
        walletId = wallet,
        targetWalletId = m["targetWallet"] as? String,
        targetAmount = (m["targetAmount"] as? Number)?.toDouble(),
        targetCurrency = m["targetCurrency"] as? String,
        category = m["category"] as? String ?: "",
        subcategory = m["subcategory"] as? String,
        comment = m["comment"] as? String,
        tags = tags,
        createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
    )
}
