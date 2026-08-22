package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class ProfileAppearance(val nickname: String = "", val avatar: String = "")

class ProfileAppearanceRepository(private val firestore: FirebaseFirestore) {
    private fun financeDoc(uid: String, profileId: String) = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profileId))

    suspend fun load(uid: String, profileId: String): ProfileAppearance {
        val profile = financeDoc(uid, profileId).get().await().get("profile") as? Map<*, *> ?: return ProfileAppearance()
        return ProfileAppearance(profile["nickname"] as? String ?: "", profile["avatar"] as? String ?: "")
    }

    suspend fun save(uid: String, profileId: String, appearance: ProfileAppearance) {
        financeDoc(uid, profileId).set(
            mapOf(
                "profile" to mapOf("nickname" to appearance.nickname.trim(), "avatar" to appearance.avatar),
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
