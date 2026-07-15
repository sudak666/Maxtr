package ua.zminka.app.data.repository

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.zminka.app.MainActivity
import ua.zminka.app.R

/**
 * Receives the same server-sent pushes functions/index.js's
 * notificationSweep already sends to web clients via push_tokens/{uid}
 * (see CLAUDE.md's Notifications section) - three checks (daily reminder,
 * budget exceeded, upcoming recurring payment/debt due date), same backend,
 * this is just a second client type registered against the same tokens
 * collection.
 */
class ZminkaMessagingService : FirebaseMessagingService() {
    private val pushRepo = PushRepository()

    // onNewToken can fire before a user is signed in (e.g. right after
    // install) - silently skip in that case. MainScreen's own
    // registerCurrentToken() call (see MainScreen.kt) covers the case
    // where a token was already minted earlier and this callback never
    // fires again for it, same idea as the web client's fbLoadNow() not
    // depending solely on a one-time getToken() call either.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { pushRepo.writeToken(uid, token) }
        }
    }

    // sendPush() (functions/index.js) only ever sends a `notification`
    // payload (no `data`), so per FCM's own delivery rules this callback
    // only fires while the app is in the foreground - a backgrounded app
    // gets the system tray notification automatically from the FCM SDK
    // itself (using this app's default notification icon, since
    // webpush.notification.icon is a web-only field FCM's Android path
    // ignores - see CLAUDE.md's push-icon note). This mirrors
    // js/notifications.js's getMessagingInstance() onMessage handler,
    // which likewise only fires for foreground messages and manually
    // builds a local notification for exactly the same reason.
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val notification = message.notification ?: return
        showNotification(notification.title ?: "Zminka", notification.body ?: "")
    }

    private fun showNotification(title: String, body: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // POST_NOTIFICATIONS is declared in AndroidManifest.xml and
        // requested at runtime from MainActivity (Android 13+) - if the
        // user denied it, NotificationManagerCompat.notify() is a no-op
        // (it internally re-checks the permission before posting), so no
        // extra guard is needed here.
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val PUSH_CHANNEL_ID = "zminka_push"
    }
}
