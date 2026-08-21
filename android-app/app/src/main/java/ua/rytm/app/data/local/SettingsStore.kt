package ua.rytm.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Mirrors js/theme-preinit.js/js/classic-globals.js's mxTheme localStorage key:
// a device-global (not per-account) light/dark toggle, no "system" option
// (the PWA only ever has the two, defaulting dark — see applyTheme()'s
// `if(!THEME_ICON[theme]) theme='dark'` fallback). DataStore Preferences is
// this app's equivalent of the PWA's device-global localStorage keys.
private val Context.settingsDataStore by preferencesDataStore(name = "rytm_settings")

class SettingsStore(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")

    val isDarkTheme: Flow<Boolean> = context.settingsDataStore.data.map { it[darkThemeKey] ?: true }

    suspend fun setDarkTheme(dark: Boolean) {
        context.settingsDataStore.edit { it[darkThemeKey] = dark }
    }

    // Mirrors js/notifications.js's pushEnabledKey() (`mx_pushEnabled_<uid>`
    // in localStorage) — per-account, same uid-prefixed-key-in-one-shared-
    // DataStore convention PinStore already established (a second account on
    // the same device gets its own independent toggle state). This is purely
    // a local "did this device register a token" flag for the Settings UI's
    // own checked state — PushRepository's Firestore writes are the source
    // of truth for whether push actually fires.
    private fun pushEnabledKey(uid: String) = booleanPreferencesKey("push_enabled_$uid")

    fun isPushEnabled(uid: String): Flow<Boolean> = context.settingsDataStore.data.map { it[pushEnabledKey(uid)] ?: false }

    suspend fun setPushEnabled(uid: String, enabled: Boolean) {
        context.settingsDataStore.edit { it[pushEnabledKey(uid)] = enabled }
    }
}
