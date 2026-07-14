package ua.zminka.app.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import ua.zminka.app.data.model.Debt
import ua.zminka.app.data.model.DebtData
import ua.zminka.app.data.model.DebtDoc
import ua.zminka.app.data.profile.ProfileManager

/**
 * Mirrors js/color-picker.js's fbLoadNow()/fbSaveNow() handling of the
 * `debt` doc. Doc name is suffixed per the active profile via
 * [ProfileManager.suffixedDocName], same scheme as FinanceRepository/
 * ShiftsRepository; `observeDebt()` re-subscribes on profile switch the
 * same way too.
 *
 * Unlike ShiftsRepository, writes here replace the whole `data` object
 * (debts + currentDebtId) in one go, same as the web client —
 * `entries`/`debts` are nested arrays, which Firestore can't partially
 * update at a single-item level anyway (dot-path updates only address
 * map fields, not array elements by id), so there's no equivalent
 * partial-update shortcut here the way ShiftsRepository's `data.<dateKey>`
 * update has.
 */
class DebtRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private companion object {
        const val DCOL = "max_tracker"
        const val DEBT_DOC = "debt"
    }

    private fun debtDocRef(uid: String): DocumentReference =
        db.collection("users").document(uid).collection(DCOL).document(ProfileManager.suffixedDocName(DEBT_DOC))

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDebt(uid: String): Flow<DebtDoc> = ProfileManager.activeProfileId.flatMapLatest {
        callbackFlow {
            val reg = debtDocRef(uid).addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(DebtDoc::class.java) ?: DebtDoc())
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun saveDebts(uid: String, debts: List<Debt>, currentDebtId: Long?) {
        val doc = DebtDoc(data = DebtData(debts, currentDebtId), updatedAt = System.currentTimeMillis())
        debtDocRef(uid).set(doc).await()
    }
}
