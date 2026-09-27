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
        val result = withContext(Dispatchers.Default) { PinHash.verify(stored, rawPin) }
        if (result == PinHash.Verify.OK_LEGACY) {
            val upgraded = hashV2(rawPin)
            context.pinDataStore.edit { it[pinKey(uid)] = upgraded }
        }
        return result != PinHash.Verify.WRONG
    }

    /** Epoch ms until which PIN entry is locked out, or 0. */
    suspend fun lockedUntil(uid: String): Long = context.pinDataStore.data.first()[lockKey(uid)] ?: 0L

    /** Records a real (full-length) wrong PIN; returns the lockout deadline (0 if none). */
    suspend fun registerFailure(uid: String): Long {
        var until = 0L
        context.pinDataStore.edit {
            val fails = (it[failKey(uid)] ?: 0) + 1
            it[failKey(uid)] = fails
            if (fails >= PinHash.FREE_ATTEMPTS) {
                until = System.currentTimeMillis() + PinHash.lockDelayMs(fails)
                it[lockKey(uid)] = until
            }
        }
        return until
    }

    suspend fun clearFailures(uid: String) {
        context.pinDataStore.edit { it.remove(failKey(uid)); it.remove(lockKey(uid)) }
    }

    private suspend fun hashV2(rawPin: String): String = withContext(Dispatchers.Default) { PinHash.encode(rawPin) }

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

/** Pure PIN hashing/lockout logic, kept Android-free so JVM unit tests cover it. */
internal object PinHash {
    const val ITERATIONS = 120_000
    const val FREE_ATTEMPTS = 5
    private const val BASE_LOCK_MS = 30_000L
    private const val MAX_LOCK_MS = 60 * 60_000L

    enum class Verify { OK, OK_LEGACY, WRONG }

    // java.util.Base64's basic encoder == android.util.Base64.NO_WRAP, so
    // hashes written by earlier builds still parse.
    fun encode(rawPin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }, iterations: Int = ITERATIONS): String {
        val b64 = java.util.Base64.getEncoder()
        return "v2$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(pbkdf2(rawPin, salt, iterations))
    }

    fun verify(stored: String, rawPin: String): Verify {
        if (stored.startsWith("v2$")) {
            val parts = stored.split('$')
            if (parts.size != 4) return Verify.WRONG
            val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return Verify.WRONG
            val b64 = java.util.Base64.getDecoder()
            val salt = runCatching { b64.decode(parts[2]) }.getOrNull() ?: return Verify.WRONG
            val expected = runCatching { b64.decode(parts[3]) }.getOrNull() ?: return Verify.WRONG
            return if (MessageDigest.isEqual(pbkdf2(rawPin, salt, iterations), expected)) Verify.OK else Verify.WRONG
        }
        return if (MessageDigest.isEqual(stored.toByteArray(), sha256Hex(rawPin).toByteArray())) Verify.OK_LEGACY else Verify.WRONG
    }

    /** Lockout after the [fails]-th wrong PIN (>= FREE_ATTEMPTS): 30s doubling, capped at 1h. */
    fun lockDelayMs(fails: Int): Long = minOf(BASE_LOCK_MS shl minOf(fails - FREE_ATTEMPTS, 7), MAX_LOCK_MS)

    private fun pbkdf2(rawPin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(rawPin.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
