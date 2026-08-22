package ua.rytm.app.data

import org.junit.Test
import ua.rytm.app.data.local.pinLockoutMs
import kotlin.test.assertEquals

class PinSecurityTest {
    @Test fun lockoutEscalatesAndCaps() {
        assertEquals(0L, pinLockoutMs(4))
        assertEquals(30_000L, pinLockoutMs(5))
        assertEquals(60_000L, pinLockoutMs(6))
        assertEquals(15 * 60_000L, pinLockoutMs(20))
    }
}
