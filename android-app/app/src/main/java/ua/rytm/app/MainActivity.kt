package ua.rytm.app

import android.os.Bundle
import android.os.Build
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import ua.rytm.app.navigation.RytmNavHost
import ua.rytm.app.ui.screens.auth.AuthViewModel
import ua.rytm.app.ui.screens.auth.LoginScreen
import ua.rytm.app.ui.screens.pin.PinLockScreen
import ua.rytm.app.ui.screens.pin.PinViewModel
import ua.rytm.app.ui.screens.onboarding.OnboardingScreen
import ua.rytm.app.ui.theme.RytmTheme
import ua.rytm.app.ui.LocalHideAmounts
import ua.rytm.app.ui.applyAppLanguage
import ua.rytm.app.ui.LocalReducedMotion
import ua.rytm.app.ui.rememberReducedMotion
import androidx.compose.runtime.mutableStateOf
import ua.rytm.app.navigation.LaunchRequest
import ua.rytm.app.navigation.parseLaunchRequest

// FragmentActivity (not plain ComponentActivity) — androidx.biometric's
// BiometricPrompt requires a FragmentActivity host for the PIN screen's
// fingerprint/face fallback (see ui/screens/pin/BiometricUtil.kt). Compose's
// setContent extension works identically on either base class.
class MainActivity : FragmentActivity() {
    private val launchRequest = mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchRequest.value = parseLaunchRequest(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()
        val app = application as RytmApplication
        setContent {
            val darkTheme by app.settingsStore.isDarkTheme.collectAsState(initial = true)
            val hideAmounts by app.settingsStore.hideAmounts.collectAsState(initial = false)
            val language by app.settingsStore.language.collectAsState(initial = "uk")
            val reducedMotion = rememberReducedMotion()
            LaunchedEffect(language) { if (applyAppLanguage(this@MainActivity, language)) recreate() }
            RytmTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalHideAmounts provides hideAmounts, LocalReducedMotion provides reducedMotion) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel: AuthViewModel = viewModel()
                    val uid = authViewModel.currentUser?.uid
                    if (uid == null) {
                        LoginScreen(authViewModel)
                    } else {
                        // Seed every domain before the initial sync and reuse the same
                        // ordered path for sign-in and profile switching.
                        LaunchedEffect(uid) {
                            app.profileSyncCoordinator.loadOnSignIn(uid)
                        }
                        DisposableEffect(uid) {
                            onDispose { app.profileSyncCoordinator.stopRealtimeSync() }
                        }

                        // PIN re-lock gate, between Auth and the main nav — mirrors
                        // js/auth.js's checkPinLock(), a local gate on top of the
                        // already-signed-in session, not a second login.
                        // Explicit Activity-scoped viewModelStoreOwner: SettingsScreen
                        // (reached via RytmNavHost, a different composition scope)
                        // creates a PinViewModel for the same purpose too, and both
                        // need to resolve to the SAME instance — otherwise setting a
                        // new PIN in Settings wouldn't mark this gate's own isUnlocked
                        // true, and the app would immediately re-lock right after the
                        // user just set their PIN.
                        val pinViewModel: PinViewModel = viewModel(
                            factory = PinViewModel.factory(app.pinStore, uid),
                            viewModelStoreOwner = this@MainActivity,
                        )
                        DisposableEffect(pinViewModel) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_STOP) pinViewModel.lockNow()
                            }
                            lifecycle.addObserver(observer)
                            onDispose { lifecycle.removeObserver(observer) }
                        }
                        // Nullable initial value on purpose: collapsing "still reading
                        // DataStore" and "confirmed no PIN set" into the same `false`
                        // would flash the real app content for a frame before a PIN
                        // gate kicks in — a real gap for a security feature. Render
                        // nothing until the read actually completes.
                        val hasPin by pinViewModel.hasPin.collectAsState(initial = null)
                        val onboardingComplete by app.settingsStore.onboardingComplete.collectAsState(initial = null)
                        when {
                            onboardingComplete == null -> {}
                            onboardingComplete == false -> OnboardingScreen(onComplete = {
                                lifecycleScope.launch { app.settingsStore.setOnboardingComplete(true) }
                            })
                            hasPin == null -> {}
                            hasPin == true && !pinViewModel.isUnlocked -> PinLockScreen(pinViewModel)
                            else -> RytmNavHost(
                                launchRequest = launchRequest.value,
                                onLaunchRequestConsumed = { request ->
                                    if (launchRequest.value?.nonce == request.nonce) launchRequest.value = null
                                },
                            )
                        }
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest.value = parseLaunchRequest(intent)
    }
}
