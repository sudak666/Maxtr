package ua.rytm.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val BACKUP_MAGIC = "RYTMBK01".toByteArray(Charsets.US_ASCII)
private const val BACKUP_ITERATIONS = 210_000
private const val MAX_ENCRYPTED_BACKUP_BYTES = 64 * 1024 * 1024

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCrypto {
    fun encrypt(plaintext: ByteArray, password: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        try {
            require(password.size >= 8) { "Password must contain at least 8 characters" }
            require(plaintext.size <= MAX_ENCRYPTED_BACKUP_BYTES)
            val salt = ByteArray(16).also(random::nextBytes)
            val iv = ByteArray(12).also(random::nextBytes)
            val key = derive(password, salt, BACKUP_ITERATIONS)
            val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                updateAAD(BACKUP_MAGIC)
                doFinal(plaintext)
            }
            return ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { out ->
                    out.write(BACKUP_MAGIC)
                    out.writeInt(BACKUP_ITERATIONS)
                    out.writeByte(salt.size)
                    out.writeByte(iv.size)
                    out.write(salt)
                    out.write(iv)
                    out.write(encrypted)
                }
                bytes.toByteArray()
            }
        } finally {
            password.fill('\u0000')
        }
    }

    fun decrypt(payload: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "Password must contain at least 8 characters" }
        if (payload.size !in (BACKUP_MAGIC.size + 4 + 2 + 16 + 12 + 16)..MAX_ENCRYPTED_BACKUP_BYTES) {
            password.fill('\u0000')
            throw InvalidBackupException("Invalid backup size")
        }
        try {
            return DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val magic = ByteArray(BACKUP_MAGIC.size).also(input::readFully)
                if (!magic.contentEquals(BACKUP_MAGIC)) throw InvalidBackupException("Invalid backup header")
                val iterations = input.readInt()
                if (iterations !in 100_000..1_000_000) throw InvalidBackupException("Invalid KDF parameters")
                val saltSize = input.readUnsignedByte()
                val ivSize = input.readUnsignedByte()
                if (saltSize != 16 || ivSize != 12) throw InvalidBackupException("Invalid cipher parameters")
                val salt = ByteArray(saltSize).also(input::readFully)
                val iv = ByteArray(ivSize).also(input::readFully)
                val encrypted = input.readBytes()
                val key = derive(password, salt, iterations)
                try {
                    Cipher.getInstance("AES/GCM/NoPadding").run {
                        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                        updateAAD(BACKUP_MAGIC)
                        doFinal(encrypted)
                    }
                } catch (error: AEADBadTagException) {
                    throw InvalidBackupException("Wrong password or damaged backup", error)
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
