package ua.rytm.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

// Mirrors js/theme-preinit.js/js/classic-globals.js's mxTheme localStorage key:
// a device-global (not per-account) light/dark toggle, no "system" option
// (the PWA only ever has the two, defaulting dark — see applyTheme()'s
// `if(!THEME_ICON[theme]) theme='dark'` fallback). DataStore Preferences is
// this app's equivalent of the PWA's device-global localStorage keys.
private val Context.settingsDataStore by preferencesDataStore(name = "rytm_settings")

class SettingsStore(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val hideAmountsKey = booleanPreferencesKey("hide_amounts")
    private val privacyCacheKey = booleanPreferencesKey("privacy_cache")
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")
    private val languageKey = stringPreferencesKey("language")

    val isDarkTheme: Flow<Boolean> = context.settingsDataStore.data.map { it[darkThemeKey] ?: true }

    suspend fun setDarkTheme(dark: Boolean) {
        context.settingsDataStore.edit { it[darkThemeKey] = dark }
    }

    val hideAmounts: Flow<Boolean> = context.settingsDataStore.data.map { it[hideAmountsKey] ?: false }
    suspend fun setHideAmounts(hidden: Boolean) { context.settingsDataStore.edit { it[hideAmountsKey] = hidden } }

    val privacyCacheEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[privacyCacheKey] ?: true }
    suspend fun setPrivacyCacheEnabled(enabled: Boolean) { context.settingsDataStore.edit { it[privacyCacheKey] = enabled } }
    suspend fun isPrivacyCacheEnabled(): Boolean = privacyCacheEnabled.first()

    val onboardingComplete: Flow<Boolean> = context.settingsDataStore.data.map { it[onboardingCompleteKey] ?: false }
    suspend fun setOnboardingComplete(complete: Boolean) { context.settingsDataStore.edit { it[onboardingCompleteKey] = complete } }
    val language: Flow<String> = context.settingsDataStore.data.map { if (it[languageKey] == "en") "en" else "uk" }
    suspend fun setLanguage(language: String) { context.settingsDataStore.edit { it[languageKey] = if (language == "en") "en" else "uk" } }

    private fun biometricOnboardingDismissedKey(uid: String) = booleanPreferencesKey("biometric_onboarding_dismissed_$uid")
    fun biometricOnboardingDismissed(uid: String): Flow<Boolean> = context.settingsDataStore.data.map { it[biometricOnboardingDismissedKey(uid)] ?: false }
    suspend fun setBiometricOnboardingDismissed(uid: String, dismissed: Boolean) {
        context.settingsDataStore.edit { it[biometricOnboardingDismissedKey(uid)] = dismissed }
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

    private val ratesSourceKey = stringPreferencesKey("rates_source")
    private val ratesUpdatedAtKey = longPreferencesKey("rates_updated_at")
    val ratesSource: Flow<String> = context.settingsDataStore.data.map { it[ratesSourceKey] ?: "nbu" }
    val ratesUpdatedAt: Flow<Long?> = context.settingsDataStore.data.map { it[ratesUpdatedAtKey] }

    suspend fun setRatesSource(source: String) {
        context.settingsDataStore.edit { it[ratesSourceKey] = if (source == "privat") "privat" else "nbu" }
    }

    suspend fun setRatesUpdatedAt(value: Long) {
        context.settingsDataStore.edit { it[ratesUpdatedAtKey] = value }
    }

    private val widgetsEnabledKey = stringPreferencesKey("finance_widgets_enabled")
    private val widgetsOrderKey = stringPreferencesKey("finance_widgets_order")
    private val cryptoCacheKey = stringPreferencesKey("crypto_top_cache")
    private val cryptoCacheAtKey = longPreferencesKey("crypto_top_cache_at")

    val financeWidgets: Flow<FinanceWidgetsConfig> = context.settingsDataStore.data.map { prefs ->
        val enabled = prefs[widgetsEnabledKey]?.split(',')?.filter { it in FINANCE_WIDGET_KEYS }?.toSet()
            ?: FINANCE_WIDGET_KEYS.toSet()
        val storedOrder = prefs[widgetsOrderKey]?.split(',')?.filter { it in FINANCE_WIDGET_KEYS }.orEmpty()
        FinanceWidgetsConfig(enabled, (storedOrder + FINANCE_WIDGET_KEYS).distinct())
    }

    suspend fun setWidgetEnabled(key: String, enabled: Boolean) {
        require(key in FINANCE_WIDGET_KEYS)
        context.settingsDataStore.edit { prefs ->
            val values = prefs[widgetsEnabledKey]?.split(',')?.filter { it in FINANCE_WIDGET_KEYS }?.toMutableSet()
                ?: FINANCE_WIDGET_KEYS.toMutableSet()
            if (enabled) values += key else values -= key
            prefs[widgetsEnabledKey] = FINANCE_WIDGET_KEYS.filter { it in values }.joinToString(",")
        }
    }

    suspend fun moveWidget(key: String, direction: Int) {
        require(key in FINANCE_WIDGET_KEYS)
        context.settingsDataStore.edit { prefs ->
            val order = (prefs[widgetsOrderKey]?.split(',')?.filter { it in FINANCE_WIDGET_KEYS }.orEmpty() + FINANCE_WIDGET_KEYS).distinct().toMutableList()
            val from = order.indexOf(key)
            val to = from + direction
            if (from >= 0 && to in order.indices) {
                order[from] = order[to].also { order[to] = order[from] }
                prefs[widgetsOrderKey] = order.joinToString(",")
            }
        }
    }

    suspend fun replaceFinanceWidgets(config: FinanceWidgetsConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[widgetsEnabledKey] = FINANCE_WIDGET_KEYS.filter { it in config.enabled }.joinToString(",")
            prefs[widgetsOrderKey] = (config.order.filter { it in FINANCE_WIDGET_KEYS } + FINANCE_WIDGET_KEYS).distinct().joinToString(",")
        }
    }

    suspend fun getFinanceWidgets(): FinanceWidgetsConfig = financeWidgets.first()

    val cryptoCache: Flow<CryptoCache?> = context.settingsDataStore.data.map { prefs ->
        prefs[cryptoCacheKey]?.let { CryptoCache(it, prefs[cryptoCacheAtKey] ?: 0L) }
    }

    suspend fun setCryptoCache(json: String, at: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[cryptoCacheKey] = json
            prefs[cryptoCacheAtKey] = at
        }
    }
}

val FINANCE_WIDGET_KEYS = listOf("goals", "dailyTip", "cryptoTop")
data class FinanceWidgetsConfig(val enabled: Set<String>, val order: List<String>)
data class CryptoCache(val json: String, val at: Long)
