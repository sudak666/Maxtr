package ua.rytm.app.ui

import android.app.Activity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

// Durable (SharedPreferences, not an in-memory var — a fresh process would
// otherwise force one needless recreate per cold launch) record of the
// user's chosen language, used two ways: (1) [applyAppLanguage] reads it to
// decide whether a real change is being requested at all (never re-derived
// from a system getter — see the real bug that caused below), and (2)
// [wrapContext] reads it synchronously in `attachBaseContext()`, before
// Compose/DataStore are available, to force every Activity's own Resources
// to the chosen language directly — this is the part that actually makes
// text switch language, independent of whether the OS cooperates.
private const val PREFS_NAME = "app_language_guard"
private const val KEY_CHOSEN_TAG = "chosen_tag"

/**
 * Wraps an Activity's `attachBaseContext(base)` argument so every Resources
 * lookup in that Activity (`stringResource()` included) resolves against the
 * user's chosen language, regardless of the platform's own per-app locale
 * state.
 *
 * This was added after [applyAppLanguage]'s [AppCompatDelegate.setApplicationLocales]-only
 * approach was verified live to not work: reported by the account owner on
 * a real Android 13 Samsung (One UI 5.1) device — toggling the language
 * caused a single screen flicker (the `recreate()` genuinely fired) but the
 * actual text never changed. Independently reproduced on a Pixel emulator.
 * In both cases `adb shell cmd locale get-app-locales` stayed empty no
 * matter what — i.e. the platform-level `LocaleManager` state the AndroidX
 * API is supposed to set never actually persisted, on two unrelated
 * environments, despite the app's own manifest/`locales_config.xml`/resource
 * wiring all verified correct (`aapt2 dump`) in the built APK. Rather than
 * keep trusting that OS mechanism to do the actual work, this directly
 * forces the Activity's own Configuration — the same technique apps used
 * for in-app language switching before the per-app-language API existed,
 * and one that doesn't depend on any OS service cooperating.
 * [AppCompatDelegate.setApplicationLocales] is still called too (see
 * [applyAppLanguage]) since it's harmless and is what makes Rytm surface
 * correctly under system Settings → App languages *if* the OS does
 * cooperate — this wrapper is what guarantees the visible text either way.
 */
fun wrapContext(base: Context): Context {
    val prefs = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // Android 13+: a language picked in system Settings → App languages is the
    // platform's per-app locale; it wins over the in-app choice and is copied
    // into it, so the two can't disagree (it used to be silently overridden).
    val systemTag = platformAppTag(base)
    if (systemTag != null && systemTag != prefs.getString(KEY_CHOSEN_TAG, null)) {
        prefs.edit().putString(KEY_CHOSEN_TAG, systemTag).apply()
    }
    val tag = systemTag ?: prefs.getString(KEY_CHOSEN_TAG, null) ?: return base
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}

/**
 * Applies the in-app language choice: persists the chosen tag (read by
 * [wrapContext] on the next `attachBaseContext()`), best-effort asks
 * [AppCompatDelegate] to also set the platform per-app locale, and reports
 * whether MainActivity should call `recreate()` — `true` only when the
 * requested tag actually differs from what's already stored, so a repeated
 * call for the same language (e.g. on every fresh composition after a
 * recreate) is a no-op instead of recursing into another recreate.
 */
fun applyAppLanguage(activity: Activity, language: String): Boolean {
    val tag = if (language == "en") "en" else "uk"
    val prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
    if (prefs.getString(KEY_CHOSEN_TAG, null) == tag) return false
    prefs.edit().putString(KEY_CHOSEN_TAG, tag).apply()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // The framework API directly (MainActivity is a FragmentActivity, and
        // the AppCompat route never persisted it on the owner's Samsung), so
        // system Settings → App languages shows the same choice.
        activity.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(tag)
    } else {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
    return true
}

/** In-app toggle on Android 13+: writes the platform per-app locale (the OS recreates the Activity). */
fun setSystemAppLanguage(context: Context, language: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val tag = if (language == "en") "en" else "uk"
    runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(tag) }
}

/** "uk"/"en" from the platform per-app locale (Android 13+), or null when none is set. */
fun systemAppLanguage(context: Context): String? = platformAppTag(context)

private fun platformAppTag(context: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val locales = runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
    if (locales == null || locales.isEmpty) return null
    return if (locales[0].language == "en") "en" else "uk"
}
