package ua.rytm.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    @Test fun roundTripUsesRandomizedAuthenticatedEncryption() {
        val plain = "sensitive profile data".toByteArray()
        val first = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        val second = BackupCrypto.encrypt(plain, "correct horse".toCharArray())
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, BackupCrypto.decrypt(first, "correct horse".toCharArray()))
    }

    @Test fun wrongPasswordAndTamperingAreRejected() {
        val encrypted = BackupCrypto.encrypt("data".toByteArray(), "correct horse".toCharArray())
        assertThrows(InvalidBackupException::class.java) { BackupCrypto.decrypt(encrypted, "wrong password".toCharArray()) }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(InvalidBackupException::class.java) { BackupCrypto.decrypt(encrypted, "correct horse".toCharArray()) }
    }
}
