package ua.rytm.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ua.rytm.app.R
import ua.rytm.app.data.local.PinRepository
import ua.rytm.app.ui.screens.pin.PinViewModel

private class FakePinRepository(var pin: String?, private val verifyDelayMs: Long = 0) : PinRepository {
    var failures = 0
    var lockUntil = 0L
    var removed = false
    override fun hasPin(uid: String): Flow<Boolean> = flowOf(pin != null)
    override fun isBiometricEnabled(uid: String): Flow<Boolean> = flowOf(false)
    override suspend fun setPin(uid: String, rawPin: String) { pin = rawPin }
    override suspend fun verifyPin(uid: String, rawPin: String): Boolean { delay(verifyDelayMs); return rawPin == pin }
    override suspend fun lockedUntil(uid: String): Long = lockUntil
    override suspend fun registerFailure(uid: String): Long { failures++; return lockUntil }
    override suspend fun clearFailures(uid: String) { failures = 0 }
    override suspend fun removePin(uid: String) { pin = null; removed = true }
    override suspend fun setBiometricEnabled(uid: String, enabled: Boolean) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class PinViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }
    private fun PinViewModel.type(digits: String) = digits.forEach { press(it.toString()) }

    @Test fun correctFourDigitPinUnlocks() = test {
        val vm = PinViewModel(FakePinRepository("1234"), "u")
        vm.type("1234"); advanceUntilIdle()
        assertTrue(vm.isUnlocked)
        assertEquals("", vm.pinInput)
    }

    // Regression (2026-09-28, live on an A51): digits typed while the slow
    // PBKDF2 check of the 4-digit prefix ran were dropped, so a fast
    // 6-digit PIN never matched.
    @Test fun digitsTypedDuringVerificationAreKept() = test {
        val vm = PinViewModel(FakePinRepository("123456", verifyDelayMs = 400), "u")
        vm.type("123456"); advanceUntilIdle()
        assertTrue(vm.isUnlocked)
    }

    @Test fun shortPrefixMismatchIsSilent() = test {
        val repo = FakePinRepository("123456")
        val vm = PinViewModel(repo, "u")
        vm.type("12345"); advanceUntilIdle()
        assertFalse(vm.isUnlocked)
        assertEquals(0, repo.failures)
        assertNull(vm.errorMessageRes)
        assertEquals("12345", vm.pinInput)
    }

    @Test fun wrongFullPinRegistersFailureAndClears() = test {
        val repo = FakePinRepository("1234")
        val vm = PinViewModel(repo, "u")
        vm.type("999999"); advanceUntilIdle()
        assertFalse(vm.isUnlocked)
        assertEquals(1, repo.failures)
        assertEquals(R.string.pin_error_invalid, vm.errorMessageRes)
        assertEquals("", vm.pinInput)
    }

    @Test fun lockedOutInputIsRejected() = test {
        val repo = FakePinRepository("1234").apply { lockUntil = System.currentTimeMillis() + 60_000 }
        val vm = PinViewModel(repo, "u")
        advanceUntilIdle()
        vm.type("1234"); advanceUntilIdle()
        assertFalse(vm.isUnlocked)
        assertEquals(R.string.pin_error_locked, vm.errorMessageRes)
    }

    @Test fun savePinValidatesLengthAndMatch() = test {
        val repo = FakePinRepository(null)
        val vm = PinViewModel(repo, "u")
        "12".forEach { vm.setNewPinDigit(it.toString()) }
        vm.savePin()
        assertEquals(R.string.pin_error_length, vm.errorMessageRes)
        vm.resetPinEntryFields()
        "1234".forEach { vm.setNewPinDigit(it.toString()) }
        "1235".forEach { vm.setConfirmPinDigit(it.toString()) }
        vm.savePin()
        assertEquals(R.string.pin_error_mismatch, vm.errorMessageRes)
        vm.resetPinEntryFields()
        "1234".forEach { vm.setNewPinDigit(it.toString()); vm.setConfirmPinDigit(it.toString()) }
        vm.savePin(); advanceUntilIdle()
        assertEquals("1234", repo.pin)
        assertTrue(vm.isUnlocked)
    }

    @Test fun forgotPinClearsCacheRemovesPinAndSignsOut() = test {
        val repo = FakePinRepository("1234")
        var signedOut = false
        var cleared = false
        val vm = PinViewModel(repo, "u", signOut = { signedOut = true })
        vm.forgotPin { cleared = true }; advanceUntilIdle()
        assertTrue(cleared); assertTrue(repo.removed); assertTrue(signedOut)
    }
}
