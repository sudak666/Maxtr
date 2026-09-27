package ua.rytm.app.ui.screens.pin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ua.rytm.app.data.local.PinStore
import com.google.firebase.auth.FirebaseAuth
import androidx.annotation.StringRes
import ua.rytm.app.R

// Mirrors js/auth.js's PIN section (checkPinLock()/tryUnlockPin()/setPin()/
// removePin()/openPinSettings()) — a local re-lock gate layered on top of the
// already-persisted Firebase session, gated in MainActivity between the Auth
// gate and the main nav. isUnlocked resets to false on every process start
// (in-memory only, not persisted) — same "lock on every app open" semantic
// as the PWA's per-page-load AppState.pinUnlocked.
class PinViewModel(private val pinStore: PinStore, val uid: String) : ViewModel() {

    companion object {
        fun factory(pinStore: PinStore, uid: String) = viewModelFactory {
            initializer { PinViewModel(pinStore, uid) }
        }
    }

    // Nullable so callers can distinguish "still reading DataStore" from
    // "confirmed no PIN set" — see MainActivity's own comment for why that
    // distinction matters for a security gate.
    val hasPin: Flow<Boolean?> = pinStore.hasPin(uid)
    val biometricEnabled: Flow<Boolean> = pinStore.isBiometricEnabled(uid)

    var isUnlocked by mutableStateOf(false)
        private set

    var pinInput by mutableStateOf("")
        private set

    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set
    fun consumeError() { errorMessageRes = null }

    /** Epoch ms until which entry is locked after repeated wrong PINs (0 = not locked). */
    var lockedUntil by mutableStateOf(0L)
        private set

    init {
        viewModelScope.launch { lockedUntil = pinStore.lockedUntil(uid) }
    }


    // Settings-sheet-only state (new/confirm PIN entry), kept separate from
    // the unlock-screen's own pinInput so opening Settings mid-unlock-flow
    // (impossible in practice, since Settings lives behind the lock, but
    // kept honestly separate anyway) can't cross-contaminate.
    var newPin by mutableStateOf("")
        private set
    var confirmPin by mutableStateOf("")
        private set

    fun press(digit: String) {
        // Not gated on an in-flight check: PBKDF2 takes ~0.3-0.5s on low-end
        // phones, and digits typed meanwhile used to be silently dropped.
        if (pinInput.length >= 6) return
        if (System.currentTimeMillis() < lockedUntil) { errorMessageRes = R.string.pin_error_locked; return }
        pinInput += digit
        // A saved PIN is 4-6 digits and this screen doesn't know the real
        // length in advance (only a hash is stored) — so try a real unlock
        // after every digit once there are enough to plausibly be a full PIN
        // (4), silently ignoring a mismatch until either it succeeds or the
        // 6-digit cap is hit with no match (real wrong PIN). Without this, a
        // 4- or 5-digit PIN would never auto-submit at all.
        if (pinInput.length >= 4) tryUnlock(silentIfMismatchAndNotFull = pinInput.length < 6)
    }

    fun backspace() {
        pinInput = pinInput.dropLast(1)
    }

    fun tryUnlock(silentIfMismatchAndNotFull: Boolean = false) {
        val entered = pinInput
        viewModelScope.launch {
            if (pinStore.verifyPin(uid, entered)) {
                pinStore.clearFailures(uid)
                lockedUntil = 0L
                isUnlocked = true
                errorMessageRes = null
                pinInput = ""
            } else if (!silentIfMismatchAndNotFull && pinInput == entered) {
                lockedUntil = pinStore.registerFailure(uid)
                errorMessageRes = if (lockedUntil > System.currentTimeMillis()) R.string.pin_error_locked else R.string.pin_error_invalid
                pinInput = ""
            }
        }
    }

    fun unlockWithBiometric() {
        viewModelScope.launch { pinStore.clearFailures(uid) }
        lockedUntil = 0L
        isUnlocked = true
        errorMessageRes = null
        pinInput = ""
    }

    fun lockNow() {
        isUnlocked = false
    }

    fun setNewPinDigit(digit: String) { if (newPin.length < 6) newPin += digit; errorMessageRes = null }
    fun newPinBackspace() { newPin = newPin.dropLast(1); errorMessageRes = null }
    fun setConfirmPinDigit(digit: String) { if (confirmPin.length < 6) confirmPin += digit; errorMessageRes = null }
    fun confirmPinBackspace() { confirmPin = confirmPin.dropLast(1); errorMessageRes = null }
    fun resetPinEntryFields() { newPin = ""; confirmPin = "" }

    fun savePin() {
        if (!Regex("^\\d{4,6}$").matches(newPin)) { errorMessageRes = R.string.pin_error_length; return }
        if (newPin != confirmPin) { errorMessageRes = R.string.pin_error_mismatch; return }
        viewModelScope.launch {
            pinStore.setPin(uid, newPin)
            isUnlocked = true
            resetPinEntryFields()
            errorMessageRes = null
        }
    }

    fun removePin() {
        viewModelScope.launch { pinStore.removePin(uid) }
    }

    fun forgotPin(clearSensitiveCache: suspend () -> Unit) {
        viewModelScope.launch {
            clearSensitiveCache()
            pinStore.removePin(uid)
            isUnlocked = true
            FirebaseAuth.getInstance().signOut()
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { pinStore.setBiometricEnabled(uid, enabled) }
    }
}
