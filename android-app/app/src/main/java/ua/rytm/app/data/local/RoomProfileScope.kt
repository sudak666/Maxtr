package ua.rytm.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileScope(val ownerUid: String, val profileId: String)

object RoomProfileScope {
    const val LEGACY_OWNER = ""
    const val DEFAULT_PROFILE = "default"

    @Volatile var ownerUid: String = LEGACY_OWNER
        private set
    @Volatile var profileId: String = DEFAULT_PROFILE
        private set
    private val mutable = MutableStateFlow(ProfileScope(ownerUid, profileId))
    val changes = mutable.asStateFlow()

    fun activate(ownerUid: String, profileId: String) {
        require(ownerUid.isNotBlank())
        require(profileId.isNotBlank())
        this.ownerUid = ownerUid
        this.profileId = profileId
        mutable.value = ProfileScope(ownerUid, profileId)
    }
}
