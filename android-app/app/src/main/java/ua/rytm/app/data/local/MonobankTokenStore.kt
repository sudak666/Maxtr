package ua.rytm.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local Monobank credentials encrypted by a non-exportable Android Keystore key. */
class MonobankTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(ownerUid: String, profileId: String): String? {
        val encoded = preferences.getString(storageKey(ownerUid, profileId), null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
            cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    fun write(ownerUid: String, profileId: String, token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        preferences.edit().putString(storageKey(ownerUid, profileId), Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun delete(ownerUid: String, profileId: String) {
        preferences.edit().remove(storageKey(ownerUid, profileId)).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun storageKey(ownerUid: String, profileId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$ownerUid\u0000$profileId".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFERENCES = "rytm_monobank_secrets"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rytm_monobank_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
