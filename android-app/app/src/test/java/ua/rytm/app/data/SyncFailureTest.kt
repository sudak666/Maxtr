package ua.rytm.app.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFailureTest {
    @Test fun wrappedIoFailureIsSafeAndRetryable() {
        val failure = SyncFailure.from(IllegalStateException("secret-token", IOException("private payload")))
        assertEquals(SyncFailure.Kind.NETWORK, failure.kind)
        assertTrue(failure.retryable)
        assertEquals("SYNC_NETWORK", failure.diagnosticCode)
        assertFalse(failure.toString().contains("secret-token"))
        assertFalse(failure.toString().contains("private payload"))
    }

    @Test fun permissionAndInvalidDataAreNotRetriedBlindly() {
        assertEquals(SyncFailure.Kind.PERMISSION, SyncFailure.from(SecurityException()).kind)
        assertFalse(SyncFailure.from(SecurityException()).retryable)
        assertEquals(SyncFailure.Kind.DATA, SyncFailure.from(IllegalArgumentException()).kind)
        assertFalse(SyncFailure.from(IllegalArgumentException()).retryable)
    }

    @Test fun unknownFailureGetsOnlyStableDiagnosticCode() {
        assertEquals(SyncFailure( SyncFailure.Kind.UNKNOWN, true, "SYNC_UNKNOWN"), SyncFailure.from(IllegalStateException("sensitive")))
    }
}
