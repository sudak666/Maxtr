package ua.rytm.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Mirrors js/auth.js's PIN layer: a local re-lock gate on top of the already-
// persisted Firebase session, never a replacement login flow (see CLAUDE.md's
// Auth section). PWA keys `mx_pin_<uid>`/`mx_bio_<uid>` in localStorage — this
// keys the same way in one shared DataStore file (uid-prefixed keys), so a
// second account signed into the same device gets its own independent PIN.
private val Context.pinDataStore by preferencesDataStore(name = "rytm_pin")

class PinStore(private val context: Context) {
    private fun pinKey(uid: String) = stringPreferencesKey("pin_hash_$uid")
    private fun bioKey(uid: String) = booleanPreferencesKey("bio_enabled_$uid")
    private fun failKey(uid: String) = intPreferencesKey("pin_fail_$uid")
    private fun lockKey(uid: String) = longPreferencesKey("pin_lock_until_$uid")

    fun hasPin(uid: String): Flow<Boolean> = context.pinDataStore.data.map { it[pinKey(uid)] != null }
    fun isBiometricEnabled(uid: String): Flow<Boolean> = context.pinDataStore.data.map { it[bioKey(uid)] ?: false }

    // A 4-6 digit PIN has at most 10^6 values, so the hash alone can't make
    // offline guessing expensive -- salted PBKDF2 raises the per-guess cost,
    // and the persisted failure counter/lockout below throttles the UI path.
    // Format: "v2$<iterations>$<saltB64>$<hashB64>". Legacy unsalted SHA-256
    // hashes (pre-2026-09 builds) still verify once and are upgraded in place.
    suspend fun setPin(uid: String, rawPin: String) {
        val encoded = hashV2(rawPin)
        context.pinDataStore.edit {
            it[pinKey(uid)] = encoded
            it.remove(failKey(uid)); it.remove(lockKey(uid))
        }
    }

    suspend fun verifyPin(uid: String, rawPin: String): Boolean {
        val stored = context.pinDataStore.data.first()[pinKey(uid)] ?: return false
        if (stored.startsWith("v2$")) {
            val parts = stored.split('$')
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            return MessageDigest.isEqual(pbkdf2(rawPin, salt, iterations), expected)
        }
        val ok = MessageDigest.isEqual(stored.toByteArray(), sha256Hex(rawPin).toByteArray())
        if (ok) {
            val upgraded = hashV2(rawPin)
            context.pinDataStore.edit { it[pinKey(uid)] = upgraded }
        }
        return ok
    }

    /** Epoch ms until which PIN entry is locked out, or 0. */
    suspend fun lockedUntil(uid: String): Long = context.pinDataStore.data.first()[lockKey(uid)] ?: 0L

    /** Records a real (full-length) wrong PIN; returns the lockout deadline (0 if none). */
    suspend fun registerFailure(uid: String): Long {
        var until = 0L
        context.pinDataStore.edit {
            val fails = (it[failKey(uid)] ?: 0) + 1
            it[failKey(uid)] = fails
            if (fails >= FREE_ATTEMPTS) {
                val delayMs = minOf(BASE_LOCK_MS shl minOf(fails - FREE_ATTEMPTS, 7), MAX_LOCK_MS)
                until = System.currentTimeMillis() + delayMs
                it[lockKey(uid)] = until
            }
        }
        return until
    }

    suspend fun clearFailures(uid: String) {
        context.pinDataStore.edit { it.remove(failKey(uid)); it.remove(lockKey(uid)) }
    }

    private suspend fun hashV2(rawPin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(rawPin, salt, ITERATIONS)
        return "v2$" + ITERATIONS + "$" + Base64.encodeToString(salt, Base64.NO_WRAP) + "$" + Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private suspend fun pbkdf2(rawPin: String, salt: ByteArray, iterations: Int): ByteArray = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(rawPin.toCharArray(), salt, iterations, 256)
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val ITERATIONS = 120_000
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_LOCK_MS = 60 * 60_000L
    }

    suspend fun removePin(uid: String) {
        context.pinDataStore.edit {
            it.remove(pinKey(uid))
            it.remove(failKey(uid)); it.remove(lockKey(uid))
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
