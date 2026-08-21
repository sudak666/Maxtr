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
private val Context.activeProfileDataStore by preferencesDataStore(name = "rytm_active_profile")

class ActiveProfileStore(private val context: Context) {
    private fun key(uid: String) = stringPreferencesKey("active_profile_$uid")

    fun activeProfileId(uid: String): Flow<String> =
        context.activeProfileDataStore.data.map { it[key(uid)] ?: DEFAULT_PROFILE_ID }

    suspend fun getActiveProfileId(uid: String): String =
        context.activeProfileDataStore.data.first()[key(uid)] ?: DEFAULT_PROFILE_ID

    suspend fun setActiveProfileId(uid: String, profileId: String) {
        context.activeProfileDataStore.edit { it[key(uid)] = profileId }
    }
}
