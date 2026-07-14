package ua.zminka.app.data.repository

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives the same server-sent pushes functions/index.js's notificationSweep
 * already sends to web clients via push_tokens/{uid} (see CLAUDE.md's
 * Notifications section) — token registration/display wiring is not yet
 * implemented in this MVP; this stub exists so the manifest's declared
 * service has a real class to bind to.
 */
class ZminkaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: write to push_tokens/{uid}, mirroring js/notifications.js's
        // FCM token registration, once push is wired up for this app.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // TODO: show a notification, mirroring sw.js's onBackgroundMessage
        // handler (themed per-type icon via NOTIF_ICONS — see CLAUDE.md).
    }
}
