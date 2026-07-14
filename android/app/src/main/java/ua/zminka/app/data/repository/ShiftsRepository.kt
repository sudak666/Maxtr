package ua.zminka.app.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import ua.zminka.app.data.model.ShiftsDoc
import ua.zminka.app.data.profile.ProfileManager

/**
 * Mirrors js/color-picker.js's fbLoadNow()/fbSaveNow() handling of the
 * `shifts` doc (users/{uid}/max_tracker/shifts — see CLAUDE.md's Firebase
 * data model section). Doc name is suffixed per the active profile via
 * [ProfileManager.suffixedDocName], same scheme as FinanceRepository;
 * `observeShifts()` re-subscribes on profile switch the same way too.
 *
 * Writes use Firestore dot-path field updates scoped to `data.<dateKey>`
 * rather than a whole-document {merge:false} overwrite like the web
 * client does — this app's MVP doesn't model `autoFillSchedule` or read
 * `shiftTypes` for editing, so a partial update avoids ever clobbering
 * fields this client doesn't know about.
 */
class ShiftsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private companion object {
        const val DCOL = "max_tracker"
        const val SHIFTS_DOC = "shifts"
    }

    private fun shiftsDocRef(uid: String): DocumentReference =
        db.collection("users").document(uid).collection(DCOL).document(ProfileManager.suffixedDocName(SHIFTS_DOC))

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeShifts(uid: String): Flow<ShiftsDoc> = ProfileManager.activeProfileId.flatMapLatest {
        callbackFlow {
            val reg = shiftsDocRef(uid).addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(ShiftsDoc::class.java) ?: ShiftsDoc())
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun setDayShiftTypes(uid: String, dateKey: String, shiftTypeIds: List<String>) {
        val ref = shiftsDocRef(uid)
        if (shiftTypeIds.isEmpty()) {
            ref.update(mapOf("data.$dateKey" to FieldValue.delete(), "updatedAt" to System.currentTimeMillis())).await()
        } else {
            ref.update(mapOf("data.$dateKey" to shiftTypeIds, "updatedAt" to System.currentTimeMillis())).await()
        }
    }
}
