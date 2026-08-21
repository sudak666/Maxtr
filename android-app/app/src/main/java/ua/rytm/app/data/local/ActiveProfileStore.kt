package ua.rytm.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ua.rytm.app.data.DEFAULT_PROFILE_ID

// Mirrors js/firebase-sync.js's activeProfileId persistence:
// localStorage['mx_activeProfile_'+uid] — a per-device choice, deliberately
// NOT synced to Firestore (confirmed by reading CLAUDE.md's Multiple
// profiles section: "activeProfileId itself is a per-device choice"). Same
// uid-prefixed-key-in-one-shared-DataStore convention as PinStore/
// SettingsStore's push-enabled flag, so a second account on the same
// device keeps its own independent active profile.
//
// Step 32 (shared profiles): the stored value mirrors
// js/firebase-sync.js's own loadActiveProfileId()/saveActiveProfileId()
// encoding exactly — either a bare profileId (one of this account's own
// profiles) or "ownerUid|profileId" (a shared profile someone else owns) —
// so the correct data-owner uid survives an app restart without a second
// lookup against profiles_meta.
private val Context.activeProfileDataStore by preferencesDataStore(name = "rytm_active_profile")

class ActiveProfileStore(private val context: Context) {
    private fun key(uid: String) = stringPreferencesKey("active_profile_$uid")

    private fun parseProfileId(raw: String): String = if (raw.contains("|")) raw.substringAfter("|") else raw
    private fun parseOwnerUid(raw: String): String? = if (raw.contains("|")) raw.substringBefore("|") else null

    fun activeProfileId(uid: String): Flow<String> =
        context.activeProfileDataStore.data.map { parseProfileId(it[key(uid)] ?: DEFAULT_PROFILE_ID) }

    // Null for one of this account's own profiles — needed alongside
    // activeProfileId() because a joined shared profile's id can collide
    // with this account's own profile id (e.g. both are the owner-side
    // "default" profile), so id alone can't tell two rows apart.
    fun activeProfileOwnerUid(uid: String): Flow<String?> =
        context.activeProfileDataStore.data.map { parseOwnerUid(it[key(uid)] ?: DEFAULT_PROFILE_ID) }

    suspend fun getActiveProfileId(uid: String): String =
        parseProfileId(context.activeProfileDataStore.data.first()[key(uid)] ?: DEFAULT_PROFILE_ID)

    // Null for one of this account's own profiles; the sharer's uid for a
    // shared profile this account joined.
    suspend fun getActiveProfileOwnerUid(uid: String): String? =
        parseOwnerUid(context.activeProfileDataStore.data.first()[key(uid)] ?: DEFAULT_PROFILE_ID)

    suspend fun setActiveProfile(uid: String, profileId: String, ownerUid: String? = null) {
        val raw = if (ownerUid != null) "$ownerUid|$profileId" else profileId
        context.activeProfileDataStore.edit { it[key(uid)] = raw }
    }
}
