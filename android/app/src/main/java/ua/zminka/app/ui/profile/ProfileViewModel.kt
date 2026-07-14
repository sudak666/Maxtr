package ua.zminka.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.zminka.app.data.model.Profile
import ua.zminka.app.data.profile.ProfileManager
import ua.zminka.app.data.repository.ProfileRepository

data class ProfileUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String = "default",
) {
    /** The web client always has an implicit 'default' profile even before any Firestore write — mirror that here. */
    val displayList: List<Profile>
        get() = if (profiles.any { it.id == "default" }) profiles else listOf(Profile(id = "default", name = "Я")) + profiles
}

class ProfileViewModel(
    private val repo: ProfileRepository = ProfileRepository(),
) : ViewModel() {

    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    val state: StateFlow<ProfileUiState> = uid?.let { currentUid ->
        combine(repo.observeProfilesMeta(currentUid), ProfileManager.activeProfileId) { meta, activeId ->
            ProfileUiState(profiles = meta.list, activeProfileId = activeId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())
    } ?: MutableStateFlow(ProfileUiState())

    fun switchProfile(id: String) {
        val currentUid = uid ?: return
        // A per-device local choice only — see ProfileManager's own doc
        // comment. No Firestore write needed; every repository's
        // observe*() flow re-subscribes automatically once this changes.
        ProfileManager.setActiveProfileId(currentUid, id)
    }

    fun addProfile(name: String) {
        val currentUid = uid ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val newProfile = repo.addProfile(currentUid, state.value.profiles, name)
            ProfileManager.setActiveProfileId(currentUid, newProfile.id)
        }
    }
}
