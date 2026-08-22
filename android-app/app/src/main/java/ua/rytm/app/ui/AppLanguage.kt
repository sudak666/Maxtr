package ua.rytm.app.ui

import android.app.Activity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import java.util.Locale

fun applyAppLanguage(activity: Activity, language: String): Boolean {
    val tag = if (language == "en") "en" else "uk"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        return false
    } else {
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
}
