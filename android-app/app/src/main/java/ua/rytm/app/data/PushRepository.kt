package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.TimeZone

// Mirrors js/notifications.js's `AppState.notifSettings` shape exactly
// (`{enabled,time,budgetAlerts,recurringAlerts,debtAlerts,timeZone}`).
data class NotifSettings(
    val enabled: Boolean = false,
    val time: String = "21:00",
    val budgetAlerts: Boolean = false,
    val recurringAlerts: Boolean = false,
    val debtAlerts: Boolean = false,
)

// Mirrors js/notifications.js's enablePushNotifications()/disablePushNotifications()
// (the FCM token half) plus toggleReminders()/toggleBudgetAlerts()/
// toggleRecurringAlerts()/toggleDebtAlerts() (the granular notifSettings half,
// added in step 28 — see the NotificationSettingsSheet doc comment for the
// UI half of this). Registers/unregisters this device's FCM token on the
// single shared `push_tokens/{uid}` doc (`{token, updatedAt}` — confirmed by
// reading js/notifications.js's own `setDoc()` call). One token per account,
// not per device — a pre-existing limitation (whichever device last
// registered "wins"), not something introduced here.
class PushRepository(private val firestore: FirebaseFirestore) {

    private fun financeDocRef(uid: String, profileId: String) =
        firestore.collection("users").document(uid).collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun getNotifSettings(uid: String, profileId: String = DEFAULT_PROFILE_ID): NotifSettings {
        val snap = financeDocRef(uid, profileId).get().await()
        @Suppress("UNCHECKED_CAST")
        val notif = snap.get("notifSettings") as? Map<String, Any?> ?: return NotifSettings()
        return NotifSettings(
            enabled = notif["enabled"] as? Boolean ?: false,
            time = notif["time"] as? String ?: "21:00",
            budgetAlerts = notif["budgetAlerts"] as? Boolean ?: false,
            recurringAlerts = notif["recurringAlerts"] as? Boolean ?: false,
            debtAlerts = notif["debtAlerts"] as? Boolean ?: false,
        )
    }

    // Each setter below writes only its own dotted field path via update() —
    // Firestore's well-documented "update a field in a nested object"
    // convention (a flat Map<String,Any> whose keys are dotted paths like
    // "notifSettings.budgetAlerts", identical on both the Android and Web
    // SDKs). Deliberately update(), not set(SetOptions.merge()): dotted-path
    // flattening is update()'s own explicitly documented behavior, and
    // set(merge())'s exact semantics for dotted string keys (as opposed to
    // an actual nested Map value) aren't part of the same documented
    // contract — not worth relying on an unverified assumption for a
    // correctness-sensitive write. update() throwing on a missing doc is a
    // non-issue here: the finance doc always exists by the time a
    // signed-in user reaches this settings screen (seeded well before any
    // notification setting can be touched). A plain object-literal
    // set(merge()) of the *whole* `notifSettings` map, the way
    // enable()/disable() below do it, would be wrong here regardless — these
    // 4 alert types are meant to be independently togglable, exactly like
    // the PWA's own 4 separate checkboxes, and overwriting the whole map on
    // every toggle would silently reset the other 3.
    suspend fun setDailyReminder(uid: String, enabled: Boolean, time: String, profileId: String = DEFAULT_PROFILE_ID) {
        financeDocRef(uid, profileId).update(
            mapOf("notifSettings.enabled" to enabled, "notifSettings.time" to time, "notifSettings.timeZone" to TimeZone.getDefault().id, "updatedAt" to System.currentTimeMillis()),
        ).await()
    }

    suspend fun setBudgetAlerts(uid: String, enabled: Boolean, profileId: String = DEFAULT_PROFILE_ID) {
        financeDocRef(uid, profileId).update(mapOf("notifSettings.budgetAlerts" to enabled, "updatedAt" to System.currentTimeMillis())).await()
    }

    suspend fun setRecurringAlerts(uid: String, enabled: Boolean, profileId: String = DEFAULT_PROFILE_ID) {
        financeDocRef(uid, profileId).update(mapOf("notifSettings.recurringAlerts" to enabled, "updatedAt" to System.currentTimeMillis())).await()
    }

    suspend fun setDebtAlerts(uid: String, enabled: Boolean, profileId: String = DEFAULT_PROFILE_ID) {
        financeDocRef(uid, profileId).update(mapOf("notifSettings.debtAlerts" to enabled, "updatedAt" to System.currentTimeMillis())).await()
    }

    suspend fun enable(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        val token = FirebaseMessaging.getInstance().token.await()
        firestore.collection("push_tokens").document(uid)
            .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
        financeDocRef(uid, profileId).set(
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

    suspend fun disable(uid: String, profileId: String = DEFAULT_PROFILE_ID) {
        firestore.collection("push_tokens").document(uid).delete().await()
        financeDocRef(uid, profileId).set(
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
