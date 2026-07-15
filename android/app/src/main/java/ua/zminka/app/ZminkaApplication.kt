package ua.zminka.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ua.zminka.app.data.profile.ProfileManager
import ua.zminka.app.data.repository.ZminkaMessagingService

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
        createPushNotificationChannel()
    }

    // minSdk is 26 (Android O), where a NotificationChannel is mandatory
    // before NotificationManagerCompat.notify() will actually show
    // anything (posting without one is a silent no-op, not a crash) - so
    // this always needs to run, no Build.VERSION.SDK_INT guard needed.
    // One channel covers all three push types (daily/budget/recurring/
    // debt-due) since none of them need distinct importance levels yet -
    // matches the web client's single generic Notification permission,
    // not per-type opt-outs.
    private fun createPushNotificationChannel() {
        val channel = NotificationChannel(
            ZminkaMessagingService.PUSH_CHANNEL_ID,
            "Сповіщення Zminka",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Нагадування про облік, перевищення бюджету, платежі та борги"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
