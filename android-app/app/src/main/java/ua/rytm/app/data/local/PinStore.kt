package ua.rytm.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    private val cipher = KeystoreAesGcm("rytm_pin_v2")
    private fun pinKey(uid: String) = stringPreferencesKey("pin_hash_$uid")
    private fun bioKey(uid: String) = booleanPreferencesKey("bio_enabled_$uid")
    private fun failuresKey(uid: String) = intPreferencesKey("pin_failures_$uid")
    private fun blockedUntilKey(uid: String) = longPreferencesKey("pin_blocked_until_$uid")

    fun hasPin(uid: String): Flow<Boolean> = context.pinDataStore.data.map { it[pinKey(uid)] != null }
    fun isBiometricEnabled(uid: String): Flow<Boolean> = context.pinDataStore.data.map { it[bioKey(uid)] ?: false }

    suspend fun setPin(uid: String, rawPin: String) {
        context.pinDataStore.edit {
            it[pinKey(uid)] = V2_PREFIX + cipher.encrypt(rawPin)
            it.remove(failuresKey(uid))
            it.remove(blockedUntilKey(uid))
        }
    }

    suspend fun verifyPin(uid: String, rawPin: String): PinVerification {
        val prefs = context.pinDataStore.data.first()
        val now = System.currentTimeMillis()
        val blockedUntil = prefs[blockedUntilKey(uid)] ?: 0L
        if (blockedUntil > now) return PinVerification.Locked(blockedUntil - now)
        val stored = prefs[pinKey(uid)] ?: return PinVerification.Invalid
        val expected = if (stored.startsWith(V2_PREFIX)) cipher.decrypt(stored.removePrefix(V2_PREFIX)) else null
        if (expected != null && rawPin.length < expected.length) return PinVerification.Incomplete
        val valid = expected?.let { MessageDigest.isEqual(it.toByteArray(), rawPin.toByteArray()) }
            ?: MessageDigest.isEqual(stored.toByteArray(), sha256Hex(rawPin).toByteArray())
        if (valid) {
            if (!stored.startsWith(V2_PREFIX)) setPin(uid, rawPin) else clearFailures(uid)
            return PinVerification.Valid
        }
        val failures = (prefs[failuresKey(uid)] ?: 0) + 1
        val lockMs = pinLockoutMs(failures)
        context.pinDataStore.edit {
            it[failuresKey(uid)] = failures
            if (lockMs > 0) it[blockedUntilKey(uid)] = now + lockMs
        }
        return if (lockMs > 0) PinVerification.Locked(lockMs) else PinVerification.Invalid
    }

    suspend fun removePin(uid: String) {
        context.pinDataStore.edit {
            it.remove(pinKey(uid))
            it.remove(bioKey(uid)) // biometric requires a PIN fallback to exist, same as the PWA
            it.remove(failuresKey(uid))
            it.remove(blockedUntilKey(uid))
        }
    }

    suspend fun setBiometricEnabled(uid: String, enabled: Boolean) {
        context.pinDataStore.edit { it[bioKey(uid)] = enabled }
    }

    private suspend fun clearFailures(uid: String) {
        context.pinDataStore.edit { it.remove(failuresKey(uid)); it.remove(blockedUntilKey(uid)) }
    }

    private companion object { const val V2_PREFIX = "v2:" }
}

sealed interface PinVerification {
    data object Valid : PinVerification
    data object Invalid : PinVerification
    data object Incomplete : PinVerification
    data class Locked(val remainingMs: Long) : PinVerification
}

internal fun pinLockoutMs(failures: Int): Long = when {
    failures < 5 -> 0L
    else -> (30_000L shl (failures - 5).coerceAtMost(5)).coerceAtMost(15 * 60_000L)
}

private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
