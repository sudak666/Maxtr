package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

// Mirrors js/state.js's AppState.profilesMeta / js/color-picker.js's
// addProfile()/renameProfile()/deleteProfile() — `profiles_meta` (account-
// wide, never `@profileId`-suffixed, confirmed by reading
// js/firebase-sync.js's userDoc()/saveProfilesMeta()) holds
// `{list:[{id,name,avatar,createdAt}], updatedAt}`.
//
// Scope for this step, disclosed: only entries this account owns itself
// (no `kind` field, or `kind` != "shared") are listed/switchable — a shared
// profile someone else owns needs a different data path entirely (that
// profile's docs live under the OWNER's uid, not this account's — see
// CLAUDE.md's Shared profiles section) and invite-code join/leave/role
// management aren't ported. `list()` filters shared entries out rather than
// showing an inert row for something this app can't actually open yet.
data class ProfileMeta(val id: String, val name: String, val createdAt: Long)

class ProfilesRepository(private val firestore: FirebaseFirestore) {

    private fun profilesMetaDocRef(uid: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document("profiles_meta")

    suspend fun list(uid: String): List<ProfileMeta> {
        val snap = profilesMetaDocRef(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val rawList = snap.get("list") as? List<Map<String, Any?>>
        val ownProfiles = rawList
            ?.filter { it["kind"] != "shared" }
            ?.mapNotNull { m ->
                val id = m["id"] as? String ?: return@mapNotNull null
                val name = m["name"] as? String ?: return@mapNotNull null
                ProfileMeta(id, name, (m["createdAt"] as? Number)?.toLong() ?: 0L)
            }
        // No profiles_meta doc yet (an account that predates this feature, or a
        // genuinely first-ever sign-in) — mirrors the PWA's own implicit
        // single-default-profile state rather than showing an empty list with
        // no way back to the data that's already there.
        return if (ownProfiles.isNullOrEmpty()) listOf(ProfileMeta(DEFAULT_PROFILE_ID, "Я", 0L)) else ownProfiles
    }

    suspend fun addProfile(uid: String, name: String) {
        val docRef = profilesMetaDocRef(uid)
        val snap = docRef.get().await()
        @Suppress("UNCHECKED_CAST")
        val rawList = (snap.get("list") as? List<Map<String, Any?>>)?.toMutableList() ?: defaultSeedList()
        rawList.add(mapOf("id" to newProfileId(), "name" to name, "createdAt" to System.currentTimeMillis()))
        docRef.set(mapOf("list" to rawList, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
    }

    // A profile id becomes part of a Firestore DOCUMENT NAME suffix
    // (`finance@<id>`, see ProfileDocNames.kt), not just a field value —
    // unlike every other UUID.randomUUID() usage elsewhere in this app
    // (tag/recurring ids etc, which are only ever field values), a hyphen
    // here breaks real production access: firestore.rules' own profile-doc
    // regex is `@[A-Za-z0-9_]+` (no hyphen), so a raw UUID string id would
    // make every write to that profile's docs silently PERMISSION_DENIED.
    // A real bug hit and fixed during this step's own verification, not a
    // guess — see ANDROID_MIGRATION.md's step 30 for the full account.
    // Stripping the hyphens keeps the same uniqueness with none of the
    // disallowed characters.
    private fun newProfileId(): String = UUID.randomUUID().toString().replace("-", "")

    suspend fun renameProfile(uid: String, id: String, name: String) {
        val docRef = profilesMetaDocRef(uid)
        val snap = docRef.get().await()
        @Suppress("UNCHECKED_CAST")
        val rawList = (snap.get("list") as? List<Map<String, Any?>>)?.toMutableList() ?: defaultSeedList()
        val updated = rawList.map { if (it["id"] == id) it + ("name" to name) else it }
        docRef.set(mapOf("list" to updated, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
    }

    // Mirrors js/color-picker.js's deleteProfile(): only ever removes one of
    // this account's own (non-shared) profiles, never the last one, and never
    // the currently active one — all 3 guards enforced by the caller
    // (ProfilesManagerViewModel), same split of responsibility the PWA uses
    // (deleteProfile() itself guards, renderProfilesUI() never even shows the
    // delete action when it would be blocked).
    suspend fun deleteProfile(uid: String, id: String) {
        val docRef = profilesMetaDocRef(uid)
        val snap = docRef.get().await()
        @Suppress("UNCHECKED_CAST")
        val rawList = (snap.get("list") as? List<Map<String, Any?>>)?.toMutableList() ?: return
        rawList.removeAll { it["id"] == id && it["kind"] != "shared" }
        docRef.set(mapOf("list" to rawList, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
    }

    private fun defaultSeedList(): MutableList<Map<String, Any?>> =
        mutableListOf(mapOf("id" to DEFAULT_PROFILE_ID, "name" to "Я", "createdAt" to System.currentTimeMillis()))
}
