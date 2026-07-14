package ua.zminka.app.data.profile

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mirrors js/firebase-sync.js's `activeProfileId`/`loadActiveProfileId()`/
 * `userDoc()` suffix scheme (see CLAUDE.md's "Multiple profiles per
 * account" section): the active profile is a **per-device** choice, not
 * synced to Firestore — stored under the same key shape as the web
 * client's localStorage key (`mx_activeProfile_<uid>`), just in
 * SharedPreferences instead of localStorage. `'default'` resolves to
 * unsuffixed doc names; any other profile id means every
 * `users/{uid}/max_tracker/{doc}` name gets suffixed `@<profileId>` —
 * see each repository's own `userDoc()`/doc-ref builder for where this
 * is actually applied. `profiles_meta` itself is the one doc name that's
 * **never** suffixed, same as the web client (`ProfileRepository`
 * doesn't consult this object at all, for that reason).
 *
 * A process-wide singleton (not per-repository state) so switching
 * profiles is visible to every repository/ViewModel at once, matching
 * the web app's single global `activeProfileId` — exposed as a
 * [StateFlow] specifically so repositories can `flatMapLatest` their
 * snapshot listeners off it and live-resubscribe to the new profile's
 * doc paths the moment it changes, instead of requiring a screen
 * restart.
 */
object ProfileManager {
    private const val PREFS_NAME = "zminka_profile_prefs"
    private const val DEFAULT_PROFILE_ID = "default"

    private lateinit var prefs: SharedPreferences

    private val _activeProfileId = MutableStateFlow(DEFAULT_PROFILE_ID)
    val activeProfileId: StateFlow<String> get() = _activeProfileId

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Call once a uid becomes known (right after sign-in) to load its per-device saved choice. */
    fun load(uid: String) {
        _activeProfileId.value = prefs.getString(key(uid), null) ?: DEFAULT_PROFILE_ID
    }

    fun setActiveProfileId(uid: String, profileId: String) {
        prefs.edit().putString(key(uid), profileId).apply()
        _activeProfileId.value = profileId
    }

    fun current(): String = _activeProfileId.value

    /** Mirrors userDoc()'s suffix rule exactly: 'default', or the literal `profiles_meta` name, stay unsuffixed. */
    fun suffixedDocName(baseName: String): String {
        val pid = current()
        return if (baseName == "profiles_meta" || pid == DEFAULT_PROFILE_ID) baseName else "$baseName@$pid"
    }

    private fun key(uid: String) = "mx_activeProfile_$uid"
}
