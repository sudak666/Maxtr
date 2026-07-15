package ua.zminka.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Registers this device's FCM token in push_tokens/{uid} - a top-level
 * collection (not nested under users/{uid}), the exact same doc the web
 * client writes to via js/notifications.js's enablePushNotifications(),
 * and the one functions/index.js's notificationSweep reads to push to
 * every registered client regardless of platform (see CLAUDE.md's Firebase
 * data model / Notifications sections).
 *
 * Unlike the web client, registration here isn't gated behind a separate
 * in-app toggle - POST_NOTIFICATIONS (Android 13+ runtime permission,
 * already declared in AndroidManifest.xml) is the one real gate, requested
 * once from MainActivity; once granted, getting a token is silent and
 * automatic. This matches how most native Android apps handle push (no
 * second app-level switch on top of the OS permission), rather than
 * mirroring the PWA's separate opt-in switch - there's no offline service-
 * worker-registration step to gate here either, unlike the web flow.
 */
class PushRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun registerCurrentToken(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        writeToken(uid, token)
    }

    suspend fun writeToken(uid: String, token: String) {
        // merge:true, same as js/notifications.js's setDoc(...,{merge:true})
        // - the Cloud Function's Admin SDK writes its own dedup fields
        // (sentDaily/sentBudget/sentRecurring/profileState) onto this same
        // doc; a non-merge set would silently wipe them.
        db.collection("push_tokens").document(uid)
            .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
    }
}
