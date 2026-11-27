package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.data.local.PremiumCache

/** The Premium cache gained the plan's details (2026-11-27); old entries must still read. */
class PremiumCacheTest {
    @Test fun oldTwoFieldEntryStillReads() {
        val c = PremiumCache.parse("uid1|1700000000000")!!
        assertEquals("uid1", c.uid)
        assertEquals(1_700_000_000_000L, c.validUntil)
        assertNull(c.source)
        assertFalse(c.autoRenewing)
        assertNull(c.expiresAt)
    }

    @Test fun fullEntryRoundTrips() {
        val c = PremiumCache("uid1", 1_800_000_000_000L, "play", true, "yearly", 1_800_000_000_000L)
        assertEquals(c, PremiumCache.parse(c.encode()))
    }

    @Test fun grantHasNoEnd() {
        val c = PremiumCache.parse(PremiumCache("u", 5L, "grant").encode())!!
        assertEquals("grant", c.source)
        assertNull(c.expiresAt)
    }

    @Test fun brokenEntryIsIgnored() = assertNull(PremiumCache.parse("garbage"))

    @Test fun onlyPlayIsManageable() {
        assertTrue(PremiumStatus(active = true, source = "play").manageable)
        assertFalse(PremiumStatus(active = true, source = "grant").manageable)
        assertFalse(PremiumStatus(active = true, ungated = true, source = "play").manageable)
        assertFalse(PremiumStatus(active = false, source = "play").manageable)
    }
}
