package ua.zminka.app.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ua.zminka.app.data.model.Profile
import ua.zminka.app.data.model.ProfilesMeta

/**
 * Mirrors js/firebase-sync.js's loadProfilesMeta()/saveProfilesMeta().
 * `profiles_meta` is account-wide and never suffixed by the active
 * profile (see CLAUDE.md's "Multiple profiles per account" section), so
 * — unlike FinanceRepository/ShiftsRepository/DebtRepository — this
 * repository never consults ProfileManager when building its doc ref.
 *
 * Unlike the web client, this repository does **not** auto-seed a
 * `{list:[{id:'default',...}]}` doc when one doesn't exist yet (the web
 * client's loadProfilesMeta() does, via a real write on first load) —
 * this app's MVP treats a missing/empty doc as "just show the implicit
 * default profile" without writing anything, since seeding is a
 * one-time account-setup concern the web client already owns; adding a
 * second writer for the same first-run seed risks a race with it.
 */
class ProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private companion object {
        const val DCOL = "max_tracker"
        const val PROFILES_META_DOC = "profiles_meta"
    }

    private fun profilesMetaRef(uid: String): DocumentReference =
        db.collection("users").document(uid).collection(DCOL).document(PROFILES_META_DOC)

    fun observeProfilesMeta(uid: String): Flow<ProfilesMeta> = callbackFlow {
        val reg = profilesMetaRef(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(ProfilesMeta::class.java) ?: ProfilesMeta())
        }
        awaitClose { reg.remove() }
    }

    suspend fun addProfile(uid: String, currentList: List<Profile>, name: String): Profile {
        val profile = Profile(
            id = "p_${System.currentTimeMillis()}",
            name = name.trim().ifBlank { "Профіль" },
            createdAt = System.currentTimeMillis(),
        )
        val meta = ProfilesMeta(list = currentList + profile, updatedAt = System.currentTimeMillis())
        profilesMetaRef(uid).set(meta).await()
        return profile
    }
}
