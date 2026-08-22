package ua.rytm.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationLimitsTest {
    @Test fun finiteValuesBelowLimitAreAccepted() {
        assertTrue(isStoredAmountValid(0.0))
        assertTrue(isStoredAmountValid(AMOUNT_MAX - 0.01))
        assertTrue(isStoredAmountValid(-AMOUNT_MAX + 0.01))
    }

    @Test fun boundaryAndNonFiniteValuesAreRejected() {
        assertFalse(isStoredAmountValid(AMOUNT_MAX))
        assertFalse(isStoredAmountValid(-AMOUNT_MAX))
        assertFalse(isStoredAmountValid(Double.NaN))
        assertFalse(isStoredAmountValid(Double.POSITIVE_INFINITY))
    }
}
