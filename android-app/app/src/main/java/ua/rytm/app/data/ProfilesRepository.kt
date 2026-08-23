package ua.rytm.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

// `profiles_meta` contains own profiles and references to shared profiles.
// Shared data remains under the owner's user tree and is permission-checked.
data class ProfileMeta(
    val id: String,
    val name: String,
    val createdAt: Long,
    val kind: String? = null,
    val ownerUid: String? = null,
) {
    val isShared: Boolean get() = kind == "shared"

    // The uid whose Firestore tree this profile's actual data
    // (shifts/finance/debt/...) lives under — the signed-in account's own
    // uid for a normal profile, or the sharer's uid for a joined one.
    fun dataOwnerUid(signedInUid: String): String = if (isShared) (ownerUid ?: signedInUid) else signedInUid
}

sealed class RedeemInviteResult {
    data class Ok(val ownerUid: String, val profileId: String, val profileName: String) : RedeemInviteResult()
    data class Failed(val reason: String) : RedeemInviteResult()
}

// Mirrors js/firebase-sync.js's listSharedMembers() return shape — a
// member absent from `roles` defaults to 'editor' (see firestore.rules'
// memberRole(), the actual enforcement; this is only ever a UI mirror).
data class SharedMemberInfo(val members: List<String>, val roles: Map<String, String>)

class ProfilesRepository(private val firestore: FirebaseFirestore) {

    companion object {
        // No 0/O/1/I — unambiguous to read aloud or type, matches
        // js/firebase-sync.js's INVITE_CODE_ALPHABET exactly.
        private const val INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val INVITE_TTL_MS = 24 * 60 * 60 * 1000L
    }

    private fun profilesMetaDocRef(uid: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document("profiles_meta")

    private fun sharedMembersDocRef(ownerUid: String, profileId: String) =
        firestore.collection("users").document(ownerUid).collection("max_tracker")
            .document(profileDocName("shared_members", profileId))

    private fun inviteDocRef(code: String) = firestore.collection("profile_invites").document(code)

    suspend fun list(uid: String): List<ProfileMeta> {
        val snap = profilesMetaDocRef(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val rawList = snap.get("list") as? List<Map<String, Any?>>
        val allProfiles = rawList?.mapNotNull { m ->
            val id = m["id"] as? String ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            ProfileMeta(
                id = id,
                name = name,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                kind = m["kind"] as? String,
                ownerUid = m["ownerUid"] as? String,
            )
        }
        // No profiles_meta doc yet (an account that predates this feature, or a
        // genuinely first-ever sign-in) — mirrors the PWA's own implicit
        // single-default-profile state rather than showing an empty list with
        // no way back to the data that's already there.
        return if (allProfiles.isNullOrEmpty()) listOf(ProfileMeta(DEFAULT_PROFILE_ID, "Я", 0L)) else allProfiles
    }

    suspend fun addProfile(uid: String, name: String) {
        val docRef = profilesMetaDocRef(uid)
        val snap = docRef.get().await()
        @Suppress("UNCHECKED_CAST")
        val rawList = (snap.get("list") as? List<Map<String, Any?>>)?.toMutableList() ?: defaultSeedList()
        rawList.add(mapOf("id" to newProfileId(), "name" to name, "createdAt" to System.currentTimeMillis()))
        docRef.set(mapOf("list" to rawList, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
    }

    // Profile ids become Firestore document suffixes and must satisfy the
    // rules allowlist `@[A-Za-z0-9_]+`.
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
    // (ProfilesManagerViewModel), same split of responsibility the PWA uses.
    // A shared *reference* is removed via leaveSharedProfile() instead, never
    // this function (see its own `kind != "shared"` guard below).
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

    // ── Shared profiles ─────────────────────────────────────────────
    // Mirrors js/firebase-sync.js's shareCurrentProfile()/redeemSharedInvite()/
    // leaveSharedProfile() exactly — see that file's own doc comments and
    // firestore.rules' "SHARED PROFILES helpers" section for the full design
    // (invite-code join, never open self-join; a two-step rules-enforced
    // redemption; deletion of the profile's data stays owner-only).

    // Turns one of this account's own profiles into a shared one (idempotent
    // — calling it again just issues a fresh invite code) and returns a code
    // to hand to whoever should join. Can only be called for a profile this
    // account owns (the caller only ever offers this action on a non-shared
    // profile row).
    suspend fun shareProfile(ownerUid: String, profileId: String, profileName: String): String {
        val membersRef = sharedMembersDocRef(ownerUid, profileId)
        val snap = membersRef.get().await()
        if (!snap.exists()) {
            membersRef.set(mapOf("members" to listOf(ownerUid), "updatedAt" to System.currentTimeMillis())).await()
        }
        val code = generateInviteCode()
        inviteDocRef(code).set(
            mapOf(
                "ownerUid" to ownerUid,
                "createdBy" to ownerUid,
                "profileId" to profileId,
                "profileName" to profileName,
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to System.currentTimeMillis() + INVITE_TTL_MS,
                "usedBy" to null,
            ),
            SetOptions.merge(),
        ).await()
        return code
    }

    private fun generateInviteCode(): String =
        (1..8).map { INVITE_CODE_ALPHABET.random() }.joinToString("")

    // Redeems an invite code: reads the invite, marks it used, joins
    // `shared_members`, then adds a {kind:"shared", ownerUid} reference to
    // this account's own profilesMeta so it shows up in the switcher. Returns
    // Failed(reason) for every rejected case (unknown/own/used/expired code,
    // or a write that firestore.rules itself rejects) rather than throwing —
    // same as the PWA's {ok:false, reason} return shape.
    suspend fun redeemInvite(uid: String, rawCode: String): RedeemInviteResult {
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) return RedeemInviteResult.Failed("not-found")
        val inviteRef = inviteDocRef(code)
        val snap = inviteRef.get().await()
        if (!snap.exists()) return RedeemInviteResult.Failed("not-found")
        val ownerUid = snap.getString("ownerUid") ?: return RedeemInviteResult.Failed("not-found")
        if (ownerUid == uid) return RedeemInviteResult.Failed("own-profile")
        if (snap.getString("usedBy") != null) return RedeemInviteResult.Failed("used")
        val expiresAt = snap.getLong("expiresAt") ?: 0L
        if (expiresAt < System.currentTimeMillis()) return RedeemInviteResult.Failed("expired")
        val profileId = snap.getString("profileId") ?: return RedeemInviteResult.Failed("not-found")
        val profileName = snap.getString("profileName") ?: code

        return try {
            inviteRef.update("usedBy", uid).await()
            sharedMembersDocRef(ownerUid, profileId).update(
                mapOf(
                    "members" to FieldValue.arrayUnion(uid),
                    "updatedAt" to System.currentTimeMillis(),
                    "lastJoinInviteCode" to code,
                ),
            ).await()

            val docRef = profilesMetaDocRef(uid)
            val metaSnap = docRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val rawList = (metaSnap.get("list") as? List<Map<String, Any?>>)?.toMutableList() ?: defaultSeedList()
            val alreadyThere = rawList.any { it["kind"] == "shared" && it["id"] == profileId && it["ownerUid"] == ownerUid }
            if (!alreadyThere) {
                rawList.add(
                    mapOf(
                        "id" to profileId,
                        "name" to profileName,
                        "kind" to "shared",
                        "ownerUid" to ownerUid,
                        "createdAt" to System.currentTimeMillis(),
                    ),
                )
                docRef.set(mapOf("list" to rawList, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
            }
            RedeemInviteResult.Ok(ownerUid, profileId, profileName)
        } catch (e: Exception) {
            RedeemInviteResult.Failed("failed")
        }
    }

    // Leaves a shared profile: removes this account from the owner's
    // `shared_members`, then removes the local reference from this account's
    // own profilesMeta. Mirrors js/color-picker.js's leaveSharedProfileUI() —
    // the caller must switch off this profile first (same "can't act on the
    // active profile" guard as deleteProfile()).
    suspend fun leaveSharedProfile(uid: String, ownerUid: String, profileId: String) {
        sharedMembersDocRef(ownerUid, profileId).update(
            mapOf(
                "members" to FieldValue.arrayRemove(uid),
                "updatedAt" to System.currentTimeMillis(),
            ),
        ).await()
        val docRef = profilesMetaDocRef(uid)
        val snap = docRef.get().await()
        @Suppress("UNCHECKED_CAST")
        val rawList = (snap.get("list") as? List<Map<String, Any?>>)?.toMutableList() ?: return
        rawList.removeAll { it["kind"] == "shared" && it["id"] == profileId && it["ownerUid"] == ownerUid }
        docRef.set(mapOf("list" to rawList, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
    }

    // ── Granular permissions ────────────────────────────────────────
    // Mirrors js/firebase-sync.js's listSharedMembers()/setMemberRole() —
    // owner-only (enforced by firestore.rules, not just by this function
    // only ever being called for a profile the caller owns). Returns null
    // when the profile was never shared (shared_members doc doesn't exist
    // yet), same as the PWA.
    suspend fun listSharedMembers(ownerUid: String, profileId: String): SharedMemberInfo? {
        val snap = sharedMembersDocRef(ownerUid, profileId).get().await()
        if (!snap.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val members = (snap.get("members") as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val roles = (snap.get("roles") as? Map<String, String>) ?: emptyMap()
        return SharedMemberInfo(members, roles)
    }

    suspend fun canEditProfile(signedInUid: String, ownerUid: String?, profileId: String): Boolean {
        if (ownerUid == null || ownerUid == signedInUid) return true
        val info = listSharedMembers(ownerUid, profileId) ?: return false
        return canEditProfile(signedInUid, ownerUid, info.members, info.roles)
    }

    // Writes the whole roles map in one read-modify-write, same as
    // js/firebase-sync.js's setMemberRole() — simpler to reason about than
    // a dotted-path update for a map this small.
    suspend fun setMemberRole(ownerUid: String, profileId: String, memberUid: String, role: String) {
        val ref = sharedMembersDocRef(ownerUid, profileId)
        val snap = ref.get().await()
        @Suppress("UNCHECKED_CAST")
        val existingRoles = (snap.get("roles") as? Map<String, String>) ?: emptyMap()
        val roles = existingRoles + (memberUid to role)
        ref.update(mapOf("roles" to roles, "updatedAt" to System.currentTimeMillis())).await()
    }
}
