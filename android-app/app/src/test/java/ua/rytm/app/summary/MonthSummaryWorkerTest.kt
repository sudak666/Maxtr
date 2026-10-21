package ua.rytm.app.summary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// AUDIT_2026-10 P3-02: the lock-screen notification must not show sums behind a PIN.
class MonthSummaryWorkerTest {
    @Test fun pinHidesAmountsEvenWhenHideAmountsIsOff() = assertTrue(MonthSummaryWorker.shouldHideAmounts(hideAmounts = false, hasPin = true))
    @Test fun hideAmountsHides() = assertTrue(MonthSummaryWorker.shouldHideAmounts(hideAmounts = true, hasPin = false))
    @Test fun noPinNoHideShowsAmounts() = assertFalse(MonthSummaryWorker.shouldHideAmounts(hideAmounts = false, hasPin = false))
}
