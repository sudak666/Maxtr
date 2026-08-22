package ua.rytm.app.data.local

import android.content.Context
import java.security.MessageDigest

/** Device-local Monobank credentials encrypted by a non-exportable Android Keystore key. */
class MonobankTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcm(KEY_ALIAS)

    fun read(ownerUid: String, profileId: String): String? {
        val encoded = preferences.getString(storageKey(ownerUid, profileId), null) ?: return null
        return cipher.decrypt(encoded)
    }

    fun write(ownerUid: String, profileId: String, token: String) {
        check(preferences.edit().putString(storageKey(ownerUid, profileId), cipher.encrypt(token)).commit()) {
            "Could not persist Monobank credentials"
        }
    }

    fun delete(ownerUid: String, profileId: String) {
        check(preferences.edit().remove(storageKey(ownerUid, profileId)).commit()) {
            "Could not delete Monobank credentials"
        }
    }

    private fun storageKey(ownerUid: String, profileId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$ownerUid\u0000$profileId".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFERENCES = "rytm_monobank_secrets"
        const val KEY_ALIAS = "rytm_monobank_token_v1"
    }
}
