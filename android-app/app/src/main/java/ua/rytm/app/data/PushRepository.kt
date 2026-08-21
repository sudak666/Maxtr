package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.TimeZone

// Mirrors js/notifications.js's enablePushNotifications()/disablePushNotifications():
// registers/unregisters this device's FCM token on the single shared
// `push_tokens/{uid}` doc (`{token, updatedAt}` — confirmed by reading that
// file's own `setDoc()` call). One token per account, not per device — a
// pre-existing limitation (whichever device last registered "wins"), not
// something introduced here.
//
// Deliberate scope decision for this step, not inherited from the PWA: the
// PWA's own Push toggle only ever manages the token — `notifSettings.enabled`/
// `budgetAlerts`/`recurringAlerts`/`debtAlerts` (functions/lib/sweep.js's real
// send gates, confirmed by reading it) are each flipped by their own separate
// PWA toggle, none of which have an Android UI yet. A token-only toggle here
// would look like it works (permission granted, row shows "on") but silently
// deliver nothing to an Android-only account, since every one of those flags
// defaults false server-side. Until a granular per-type settings screen
// exists on Android, this toggle turns all four on/off together with one
// shared default (21:00 daily reminder, matching the PWA's own default) — an
// honest, disclosed simplification (see ANDROID_MIGRATION.md), not a hidden
// behavior: the Settings row's own subtitle names exactly what it covers.
class PushRepository(private val firestore: FirebaseFirestore) {

    private fun financeDocRef(uid: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document("finance")

    suspend fun enable(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        firestore.collection("push_tokens").document(uid)
            .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
        financeDocRef(uid).set(
            mapOf(
                "notifSettings" to mapOf(
                    "enabled" to true,
                    "time" to "21:00",
                    "budgetAlerts" to true,
                    "recurringAlerts" to true,
                    "debtAlerts" to true,
                    "timeZone" to TimeZone.getDefault().id,
                ),
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun disable(uid: String) {
        firestore.collection("push_tokens").document(uid).delete().await()
        financeDocRef(uid).set(
            mapOf(
                "notifSettings" to mapOf(
                    "enabled" to false,
                    "budgetAlerts" to false,
                    "recurringAlerts" to false,
                    "debtAlerts" to false,
                ),
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
    }

    // Called from RytmMessagingService.onNewToken() — FCM can rotate the
    // token at any point (reinstall, app-data restore, token expiry),
    // independent of the user's own enable/disable action. Deliberately does
    // NOT touch notifSettings — a token rotation is not a re-opt-in.
    suspend fun updateToken(uid: String, token: String) {
        firestore.collection("push_tokens").document(uid)
            .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }
}
