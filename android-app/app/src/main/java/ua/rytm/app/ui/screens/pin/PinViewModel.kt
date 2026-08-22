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

    var errorMessage by mutableStateOf<String?>(null)
        private set
    fun consumeError() { errorMessage = null }

    // Settings-sheet-only state (new/confirm PIN entry), kept separate from
    // the unlock-screen's own pinInput so opening Settings mid-unlock-flow
    // (impossible in practice, since Settings lives behind the lock, but
    // kept honestly separate anyway) can't cross-contaminate.
    var newPin by mutableStateOf("")
        private set
    var confirmPin by mutableStateOf("")
        private set

    fun press(digit: String) {
        if (pinInput.length >= 6) return
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
                isUnlocked = true
                errorMessage = null
                pinInput = ""
            } else if (!silentIfMismatchAndNotFull) {
                errorMessage = "Невірний PIN-код"
                pinInput = ""
            }
        }
    }

    fun unlockWithBiometric() {
        isUnlocked = true
        errorMessage = null
        pinInput = ""
    }

    fun lockNow() {
        isUnlocked = false
    }

    fun setNewPinDigit(digit: String) { if (newPin.length < 6) newPin += digit }
    fun newPinBackspace() { newPin = newPin.dropLast(1) }
    fun setConfirmPinDigit(digit: String) { if (confirmPin.length < 6) confirmPin += digit }
    fun confirmPinBackspace() { confirmPin = confirmPin.dropLast(1) }
    fun resetPinEntryFields() { newPin = ""; confirmPin = "" }

    fun savePin() {
        if (!Regex("^\\d{4,6}$").matches(newPin)) { errorMessage = "PIN має бути 4-6 цифр"; return }
        if (newPin != confirmPin) { errorMessage = "PIN-коди не збігаються"; return }
        viewModelScope.launch {
            pinStore.setPin(uid, newPin)
            isUnlocked = true
            resetPinEntryFields()
            errorMessage = null
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
