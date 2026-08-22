package ua.rytm.app.ui.screens.pin

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ua.rytm.app.R

// Native equivalent of the PWA's WebAuthn biometric unlock (js/auth.js) — see
// CLAUDE.md's Auth section: a local re-lock convenience on top of the
// already-persisted session, never a login flow of its own. BiometricPrompt
// requires a FragmentActivity host, which is why MainActivity extends that
// instead of plain ComponentActivity (see MainActivity's own comment).

fun biometricAvailable(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
}

fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.biometric_unlock_title))
        .setSubtitle(activity.getString(R.string.biometric_unlock_subtitle))
        .setNegativeButtonText(activity.getString(R.string.action_cancel))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(info)
}
