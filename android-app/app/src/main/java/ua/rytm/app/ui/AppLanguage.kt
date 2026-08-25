package ua.rytm.app.ui

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Applies the in-app language choice.
 *
 * On API 33+ this goes through [AppCompatDelegate.setApplicationLocales],
 * which delegates to the platform `LocaleManager` — the canonical API, and
 * the one that pairs with `res/xml/locales_config.xml` (declared in the
 * manifest) to make Rytm appear under Settings → System → Languages → App
 * languages. It applies the change itself; no caller-driven `recreate()`.
 *
 * Below API 33 the old `Resources.updateConfiguration()` path is kept
 * deliberately, and it is the one case that still returns `true` (i.e. still
 * asks MainActivity to recreate). AppCompat's own pre-Tiramisu backport
 * requires the host to be an `AppCompatActivity`; MainActivity is a
 * `FragmentActivity` (androidx.biometric's requirement) running a
 * `Theme.Material` theme, and an AppCompat host would need an
 * AppCompat-derived theme it does not have. Swapping that is a real,
 * separately-verifiable change — not something to fold into a design pass
 * with no pre-33 device available to test it on, where the failure mode is
 * "language switch silently does nothing".
 */
fun applyAppLanguage(activity: Activity, language: String): Boolean {
    val tag = if (language == "en") "en" else "uk"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag) return false
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        return false
    }
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = activity.resources.configuration
    if (config.locales[0].language != tag) {
        config.setLocale(locale)
        @Suppress("DEPRECATION") activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        return true
    }
    return false
}
