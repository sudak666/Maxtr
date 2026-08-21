package ua.rytm.app.ui.screens

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
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.ProfileMeta
import ua.rytm.app.data.RedeemInviteResult

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
    var loading by mutableStateOf(true)
        private set
    var switching by mutableStateOf(false)
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set
    var pendingSwitchId by mutableStateOf<String?>(null)
        private set
    var pendingLeave by mutableStateOf<ProfileMeta?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
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

    init {
        app.activeProfileStore.activeProfileId(uid).onEach { activeProfileId = it }.launchIn(viewModelScope)
        reload()
    }

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
                errorMessage = "Не вдалося завантажити профілі"
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

    fun requestDelete(id: String) {
        // Mirrors deleteProfile()'s own guards in js/color-picker.js — checked
        // here too (not just left to the UI hiding the button), same
        // "don't trust the caller" precedent as that PWA function's own
        // comment states for the shared-profile split.
        if (id == activeProfileId) { errorMessage = "Не можна видалити активний профіль"; return }
        if (profiles.size <= 1) { errorMessage = "Має лишитись хоча б один профіль"; return }
        pendingDeleteId = id
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

    fun requestSwitch(id: String) {
        if (id == activeProfileId || switching) return
        pendingSwitchId = id
    }

    fun cancelSwitch() { pendingSwitchId = null }

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
        val id = pendingSwitchId ?: return false
        pendingSwitchId = null
        val target = profiles.find { it.id == id }
        switching = true
        return try {
            app.profileSyncCoordinator.switchProfile(uid, id, target?.let { if (it.isShared) it.ownerUid else null })
            true
        } catch (e: Exception) {
            errorMessage = "Не вдалося перемкнути профіль"
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
                errorMessage = "Не вдалося поділитися профілем"
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
                is RedeemInviteResult.Failed -> errorMessage = when (result.reason) {
                    "own-profile" -> "Це ваш власний профіль"
                    "used" -> "Цей код уже використано"
                    "expired" -> "Код прострочено"
                    "failed" -> "Не вдалося приєднатися"
                    else -> "Код не знайдено"
                }
            }
            joining = false
        }
    }

    fun requestLeave(profile: ProfileMeta) {
        if (!profile.isShared) return
        if (profile.id == activeProfileId) { errorMessage = "Спершу перемкніться на інший профіль"; return }
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
                errorMessage = "Не вдалося покинути профіль"
            }
        }
    }

    fun consumeError() { errorMessage = null }
}
