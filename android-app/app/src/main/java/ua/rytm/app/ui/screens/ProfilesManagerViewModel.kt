package ua.rytm.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.ProfileMeta
import ua.rytm.app.data.RedeemInviteResult
import ua.rytm.app.data.SharedMemberInfo

// Mirrors js/color-picker.js's profiles-modal (renderProfilesUI()):
// addProfile()/renameProfile()/deleteProfile()/switchProfile() for this
// account's own profiles, plus (step 32) shareCurrentProfile()/
// redeemSharedInvite()/leaveSharedProfile() for joining/hosting a shared
// profile — see ProfilesRepository's own doc comment for why granular
// editor/viewer roles aren't ported yet.
class ProfilesManagerViewModel(private val app: RytmApplication, private val uid: String) : ViewModel() {

    companion object {
        fun factory(app: RytmApplication, uid: String) = viewModelFactory {
            initializer { ProfilesManagerViewModel(app, uid) }
        }
    }

    var profiles by mutableStateOf<List<ProfileMeta>>(emptyList())
        private set
    var activeProfileId by mutableStateOf(DEFAULT_PROFILE_ID)
        private set
    // Null for one of this account's own profiles. Needed alongside
    // activeProfileId: a joined shared profile's id can collide with this
    // account's own profile id (both "default" is the common case), so id
    // alone can't tell which row is actually active — see isRowActive().
    var activeProfileOwnerUid by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var switching by mutableStateOf(false)
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set
    // The full ProfileMeta, not just its id — a bare id is ambiguous
    // between a joined shared profile and this account's own profile when
    // both happen to have the same id (the common "default"-vs-"default"
    // case). A real bug caught during this step's own verification: an
    // earlier version stored only the id and re-resolved it via
    // `profiles.find { it.id == id }` in confirmSwitch(), which silently
    // matched the wrong (own, not shared) profile and made a "switch to
    // shared profile" look like it succeeded while actually never leaving
    // the account's own data — see ANDROID_MIGRATION.md's step 32 for the
    // full account of how this was caught (identical-looking demo data
    // masked it at first, since a fresh account also seeds the same demo
    // numbers locally).
    var pendingSwitch by mutableStateOf<ProfileMeta?>(null)
        private set
    var pendingLeave by mutableStateOf<ProfileMeta?>(null)
        private set
    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set

    // Set right after shareProfile() succeeds; the sheet shows this in a
    // dismissible dialog so the owner can copy/read it out to whoever should
    // join. Cleared via consumeInviteCode().
    var inviteCode by mutableStateOf<String?>(null)
        private set
    var sharing by mutableStateOf(false)
        private set
    var joining by mutableStateOf(false)
        private set

    // The own profile whose "Учасники" (members) manager is currently
    // open, or null when closed — mirrors js/color-picker.js's
    // currentManagedMembersProfileId (a module-level var there since the
    // PWA's modal isn't view-model-scoped).
    var managingMembersFor by mutableStateOf<ProfileMeta?>(null)
        private set
    var members by mutableStateOf<SharedMemberInfo?>(null)
        private set
    var membersLoading by mutableStateOf(false)
        private set

    init {
        app.activeProfileStore.activeProfileId(uid).onEach { activeProfileId = it }.launchIn(viewModelScope)
        app.activeProfileStore.activeProfileOwnerUid(uid).onEach { activeProfileOwnerUid = it }.launchIn(viewModelScope)
        reload()
    }

    // profile.id alone isn't a reliable identity check — see
    // activeProfileOwnerUid's own doc comment.
    fun isRowActive(profile: ProfileMeta): Boolean =
        profile.id == activeProfileId && (if (profile.isShared) profile.ownerUid == activeProfileOwnerUid else activeProfileOwnerUid == null)

    // A real crash caught during this step's own verification, not a guess:
    // Firestore's DocumentReference.get() can throw
    // FirebaseFirestoreException("client is offline") straight through
    // .await() on a transient network blip (e.g. right as this sheet opens),
    // and with no try/catch that crashed the whole app instead of just
    // failing this one reload. This wraps the pre-existing (step 30)
    // unguarded call, not something step 32 introduced — see
    // ANDROID_MIGRATION.md's step 32 for the fuller account and why a
    // codebase-wide sweep of every other unguarded getDoc().await() call is
    // out of scope for this step.
    private fun reload() {
        viewModelScope.launch {
            loading = true
            try {
                profiles = app.profilesRepository.list(uid)
            } catch (e: Exception) {
                errorMessageRes = R.string.profiles_load_failed
            } finally {
                loading = false
            }
        }
    }

    fun addProfile(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            app.profilesRepository.addProfile(uid, clean)
            reload()
        }
    }

    fun renameProfile(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            app.profilesRepository.renameProfile(uid, id, clean)
            reload()
        }
    }

    fun requestDelete(profile: ProfileMeta) {
        // Mirrors deleteProfile()'s own guards in js/color-picker.js — checked
        // here too (not just left to the UI hiding the button), same
        // "don't trust the caller" precedent as that PWA function's own
        // comment states for the shared-profile split. Uses isRowActive(),
        // not a bare id comparison — a joined shared profile's id can
        // collide with this account's own profile id (see
        // activeProfileOwnerUid's own doc comment), which would otherwise
        // wrongly block deleting an own profile whose id happens to match
        // whatever shared profile is currently active.
        if (isRowActive(profile)) { errorMessageRes = R.string.profile_delete_active_error; return }
        if (profiles.size <= 1) { errorMessageRes = R.string.profile_delete_last_error; return }
        pendingDeleteId = profile.id
    }

    fun cancelDelete() { pendingDeleteId = null }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        pendingDeleteId = null
        viewModelScope.launch {
            app.profilesRepository.deleteProfile(uid, id)
            reload()
        }
    }

    fun requestSwitch(profile: ProfileMeta) {
        if (isRowActive(profile) || switching) return
        pendingSwitch = profile
    }

    fun cancelSwitch() { pendingSwitch = null }

    // Returns only after the new profile's full cold-sync has actually
    // finished — the caller (ProfilesManagerSheet) awaits this before
    // dismissing itself, so the sheet's own loading spinner covers the
    // whole switch instead of closing early over a still-loading screen.
    // Returns true only on real success — the caller must NOT treat this as
    // "done, dismiss and show a success toast" without checking the return
    // value first. A real bug caught during step 30's own verification: the
    // first version of this function swallowed its own exception into
    // errorMessage but the sheet still dismissed itself right after calling
    // it regardless, so a failed switch showed a false success toast with
    // the error banner never actually seen.
    suspend fun confirmSwitch(): Boolean {
        val target = pendingSwitch ?: return false
        pendingSwitch = null
        switching = true
        return try {
            app.profileSyncCoordinator.switchProfile(uid, target.id, if (target.isShared) target.ownerUid else null)
            true
        } catch (e: Exception) {
            errorMessageRes = R.string.profile_switch_failed
            false
        } finally {
            switching = false
        }
    }

    // Turns a profile this account owns into a shared one and stashes a
    // fresh invite code for the sheet to display. Idempotent — re-sharing an
    // already-shared profile just issues another code, same as
    // js/firebase-sync.js's shareCurrentProfile().
    fun shareProfile(profile: ProfileMeta) {
        if (profile.isShared || sharing) return
        viewModelScope.launch {
            sharing = true
            try {
                inviteCode = app.profilesRepository.shareProfile(uid, profile.id, profile.name)
            } catch (e: Exception) {
                errorMessageRes = R.string.profile_share_failed
            } finally {
                sharing = false
            }
        }
    }

    fun consumeInviteCode() { inviteCode = null }

    // Mirrors js/firebase-sync.js's redeemSharedInvite() error-reason mapping
    // (see js/color-picker.js's redeemInviteUI() for the exact user-facing
    // strings this echoes).
    fun joinByCode(rawCode: String) {
        val code = rawCode.trim()
        if (code.isEmpty() || joining) return
        viewModelScope.launch {
            joining = true
            when (val result = app.profilesRepository.redeemInvite(uid, code)) {
                is RedeemInviteResult.Ok -> reload()
                is RedeemInviteResult.Failed -> errorMessageRes = when (result.reason) {
                    "own-profile" -> R.string.profile_join_own_error
                    "used" -> R.string.profile_join_used_error
                    "expired" -> R.string.profile_join_expired_error
                    "failed" -> R.string.profile_join_failed
                    else -> R.string.profile_join_not_found_error
                }
            }
            joining = false
        }
    }

    fun requestLeave(profile: ProfileMeta) {
        if (!profile.isShared) return
        if (isRowActive(profile)) { errorMessageRes = R.string.profile_leave_active_error; return }
        pendingLeave = profile
    }

    fun cancelLeave() { pendingLeave = null }

    fun confirmLeave() {
        val profile = pendingLeave ?: return
        val ownerUid = profile.ownerUid ?: return
        pendingLeave = null
        viewModelScope.launch {
            try {
                app.profilesRepository.leaveSharedProfile(uid, ownerUid, profile.id)
                reload()
            } catch (e: Exception) {
                errorMessageRes = R.string.profile_leave_failed
            }
        }
    }

    fun consumeError() { errorMessageRes = null }

    // Mirrors js/color-picker.js's openSharedMembersManagerUI()/
    // renderSharedMembersList() — owner-only UI (only ever offered on one
    // of this account's own, non-shared rows). Loads (or reloads, after a
    // role change) the current member/role list.
    fun openMembersManager(profile: ProfileMeta) {
        if (profile.isShared) return
        managingMembersFor = profile
        reloadMembers()
    }

    fun closeMembersManager() {
        managingMembersFor = null
        members = null
    }

    private fun reloadMembers() {
        val profile = managingMembersFor ?: return
        viewModelScope.launch {
            membersLoading = true
            try {
                members = app.profilesRepository.listSharedMembers(uid, profile.id)
            } catch (e: Exception) {
                errorMessageRes = R.string.profile_members_load_failed
            } finally {
                membersLoading = false
            }
        }
    }

    // Mirrors js/color-picker.js's toggleMemberRoleUI(): flips
    // editor<->viewer, same as the PWA offers no third state.
    fun toggleMemberRole(memberUid: String, currentRole: String) {
        val profile = managingMembersFor ?: return
        val nextRole = if (currentRole == "viewer") "editor" else "viewer"
        viewModelScope.launch {
            try {
                app.profilesRepository.setMemberRole(uid, profile.id, memberUid, nextRole)
                reloadMembers()
            } catch (e: Exception) {
                errorMessageRes = R.string.profile_role_change_failed
            }
        }
    }
}
