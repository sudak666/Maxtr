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

// Mirrors js/color-picker.js's addProfile()/renameProfile()/deleteProfile()/
// switchProfile() — scoped to this account's own (non-shared) profiles only,
// see ProfilesRepository's own doc comment for why shared-profile join/
// leave/role-management aren't ported in this step.
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
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        app.activeProfileStore.activeProfileId(uid).onEach { activeProfileId = it }.launchIn(viewModelScope)
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            loading = true
            profiles = app.profilesRepository.list(uid)
            loading = false
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
    // value first. A real bug caught during this step's own verification:
    // the first version of this function swallowed its own exception into
    // errorMessage but the sheet still dismissed itself right after calling
    // it regardless, so a failed switch (see newProfileId()'s own doc
    // comment for the specific failure this hid) showed a false "Профіль
    // перемкнено" success toast with the error banner never actually seen.
    suspend fun confirmSwitch(): Boolean {
        val id = pendingSwitchId ?: return false
        pendingSwitchId = null
        switching = true
        return try {
            app.profileSyncCoordinator.switchProfile(uid, id)
            true
        } catch (e: Exception) {
            errorMessage = "Не вдалося перемкнути профіль"
            false
        } finally {
            switching = false
        }
    }

    fun consumeError() { errorMessage = null }
}
