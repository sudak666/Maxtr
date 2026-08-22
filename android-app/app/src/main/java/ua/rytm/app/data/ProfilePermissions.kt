package ua.rytm.app.data

internal fun canEditProfile(
    signedInUid: String,
    ownerUid: String?,
    members: Collection<String>,
    roles: Map<String, String>,
): Boolean {
    if (ownerUid == null || ownerUid == signedInUid) return true
    return signedInUid in members && (roles[signedInUid] ?: "editor") == "editor"
}
