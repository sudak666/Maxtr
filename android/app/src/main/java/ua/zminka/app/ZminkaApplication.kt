package ua.zminka.app

import android.app.Application
import ua.zminka.app.data.profile.ProfileManager

/**
 * Firebase itself is initialized automatically by the google-services
 * Gradle plugin's ContentProvider (from google-services.json) — no
 * explicit FirebaseApp.initializeApp() call needed here, unlike the web
 * client's js/core.js which does it manually since there's no such
 * plugin machinery in a browser.
 */
class ZminkaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProfileManager.init(this)
    }
}
