package ua.rytm.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.data.PushRepository

const val NOTIFICATION_CHANNEL_ID = "rytm_reminders"

// Receives the exact same push messages functions/index.js's sendPush() already
// sends to the PWA (see CLAUDE.md's Notifications section) — no server-side
// change needed for this, purely a new receiving client. sendPush() builds
// `{notification:{title,body}, webpush:{fcmOptions,notification:{icon}}}` with
// no `android`-specific config block, so a backgrounded app gets it
// auto-displayed by the system using this app's own default channel/icon
// (the meta-data in AndroidManifest.xml below) without onMessageReceived()
// ever running — that only fires when the app is in the foreground, which is
// Android's own delivery rule for a message that carries a `notification`
// payload. Mirrors js/notifications.js's onMessage() handler, which exists
// for the exact same foreground case on the PWA side.
class RytmMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // No lifecycle-scoped CoroutineScope is available in a Service
        // callback that isn't itself suspend — a short-lived IO-dispatcher
        // scope is the standard fire-and-forget pattern here, same as the
        // system gives onMessageReceived() no way to suspend either.
        CoroutineScope(Dispatchers.IO).launch {
            PushRepository(FirebaseFirestore.getInstance()).updateToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        showNotification(this, notification.title ?: getString(R.string.app_name), notification.body ?: "")
    }
}

fun ensureNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        context.getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    )
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

private fun showNotification(context: Context, title: String, body: String) {
    ensureNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    // POST_NOTIFICATIONS (API 33+) may have been revoked from system Settings
    // after this device already registered a token — NotificationManagerCompat
    // .notify() throws SecurityException in that case rather than silently
    // no-op-ing, so this guard is load-bearing, not defensive-for-show.
    val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }
}
