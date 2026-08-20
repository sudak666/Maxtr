package ua.rytm.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

// Mirrors js/auth.js's PIN layer: a local re-lock gate on top of the already-
// persisted Firebase session, never a replacement login flow (see CLAUDE.md's
// Auth section). PWA keys `mx_pin_<uid>`/`mx_bio_<uid>` in localStorage — this
// keys the same way in one shared DataStore file (uid-prefixed keys), so a
// second account signed into the same device gets its own independent PIN.
private val Context.pinDataStore by preferencesDataStore(name = "rytm_pin")

class PinStore(private val context: Context) {
    private fun pinKey(uid: String) = stringPreferencesKey("pin_hash_$uid")
    private fun bioKey(uid: String) = booleanPreferencesKey("bio_enabled_$uid")

    fun hasPin(uid: String): Flow<Boolean> = context.pinDataStore.data.map { it[pinKey(uid)] != null }
    fun isBiometricEnabled(uid: String): Flow<Boolean> = context.pinDataStore.data.map { it[bioKey(uid)] ?: false }

    suspend fun setPin(uid: String, rawPin: String) {
        context.pinDataStore.edit { it[pinKey(uid)] = sha256Hex(rawPin) }
    }

    suspend fun verifyPin(uid: String, rawPin: String): Boolean {
        val stored = context.pinDataStore.data.first()[pinKey(uid)]
        return stored != null && stored == sha256Hex(rawPin)
    }

    suspend fun removePin(uid: String) {
        context.pinDataStore.edit {
            it.remove(pinKey(uid))
            it.remove(bioKey(uid)) // biometric requires a PIN fallback to exist, same as the PWA
        }
    }

    suspend fun setBiometricEnabled(uid: String, enabled: Boolean) {
        context.pinDataStore.edit { it[bioKey(uid)] = enabled }
    }
}

private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
