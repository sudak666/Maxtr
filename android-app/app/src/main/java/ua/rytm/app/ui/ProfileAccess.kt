package ua.rytm.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ua.rytm.app.data.DEFAULT_PROFILE_ID

val LocalCanEditProfile = staticCompositionLocalOf { true }

/**
 * Who is signed in and which profile is active, resolved once in RytmNavHost.
 * Screens read this instead of calling FirebaseAuth / ActiveProfileStore
 * themselves (each screen used to re-derive it on its own).
 */
data class RytmSession(val accountUid: String?, val profileId: String, val ownerUid: String?) {
    /** Whose Firestore tree holds the active profile's data (owner's, for a shared profile). */
    val dataUid: String? get() = ownerUid ?: accountUid
}

val LocalSession = staticCompositionLocalOf { RytmSession(null, DEFAULT_PROFILE_ID, null) }
