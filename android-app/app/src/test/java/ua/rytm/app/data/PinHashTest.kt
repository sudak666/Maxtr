package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.data.local.PinHash
import java.security.MessageDigest

class PinHashTest {
    private val salt = ByteArray(16) { it.toByte() }

    @Test fun v2RoundTrip() {
        val stored = PinHash.encode("1234", salt, 1000)
        assertTrue(stored.startsWith("v2$1000$"))
        assertEquals(PinHash.Verify.OK, PinHash.verify(stored, "1234"))
        assertEquals(PinHash.Verify.WRONG, PinHash.verify(stored, "1235"))
    }

    @Test fun randomSaltDiffersPerHash() {
        assertTrue(PinHash.encode("1234", iterations = 1000) != PinHash.encode("1234", iterations = 1000))
    }

    @Test fun legacySha256VerifiesAndAsksForUpgrade() {
        val legacy = MessageDigest.getInstance("SHA-256").digest("4321".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(PinHash.Verify.OK_LEGACY, PinHash.verify(legacy, "4321"))
        assertEquals(PinHash.Verify.WRONG, PinHash.verify(legacy, "0000"))
    }

    @Test fun malformedStoredValueNeverVerifies() {
        listOf("v2$", "v2\$x\$aa\$bb", "v2$0\$AAAA\$AAAA", "v2$10$!!!$!!!", "").forEach {
            assertEquals(it, PinHash.Verify.WRONG, PinHash.verify(it, "1234"))
        }
    }

    @Test fun lockoutDoublesFromThirtySecondsAndCapsAtOneHour() {
        assertEquals(30_000L, PinHash.lockDelayMs(PinHash.FREE_ATTEMPTS))
        assertEquals(60_000L, PinHash.lockDelayMs(PinHash.FREE_ATTEMPTS + 1))
        assertEquals(120_000L, PinHash.lockDelayMs(PinHash.FREE_ATTEMPTS + 2))
        assertEquals(3_600_000L, PinHash.lockDelayMs(PinHash.FREE_ATTEMPTS + 7))
        assertEquals(3_600_000L, PinHash.lockDelayMs(PinHash.FREE_ATTEMPTS + 50))
    }
}
