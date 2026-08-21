package ua.rytm.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.navigation.RytmNavHost
import ua.rytm.app.ui.screens.auth.AuthViewModel
import ua.rytm.app.ui.screens.auth.LoginScreen
import ua.rytm.app.ui.screens.pin.PinLockScreen
import ua.rytm.app.ui.screens.pin.PinViewModel
import ua.rytm.app.ui.theme.RytmTheme

// FragmentActivity (not plain ComponentActivity) — androidx.biometric's
// BiometricPrompt requires a FragmentActivity host for the PIN screen's
// fingerprint/face fallback (see ui/screens/pin/BiometricUtil.kt). Compose's
// setContent extension works identically on either base class.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RytmApplication
        setContent {
            val darkTheme by app.settingsStore.isDarkTheme.collectAsState(initial = true)
            RytmTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel: AuthViewModel = viewModel()
                    val uid = authViewModel.currentUser?.uid
                    if (uid == null) {
                        LoginScreen(authViewModel)
                    } else {
                        // One-time cold sync on sign-in — see FinanceSyncRepository's/
                        // ShiftsSyncRepository's own doc comments for exactly what each
                        // does and doesn't do (wallets + shift types only so far, no
                        // continuous two-way sync yet).
                        //
                        // Real bug found verifying this against the Firestore emulator:
                        // each domain's seedIfEmpty() only ever ran from its own
                        // ViewModel.init, i.e. only once the user actually visited that
                        // tab — a first-time sign-in landing on Finance (the start
                        // destination) pushed real seed wallets but pushed an EMPTY
                        // shiftTypes array, since ShiftsViewModel never got created.
                        // seedIfEmpty() is idempotent (checks count()==0 first), so
                        // calling it here for every synced domain before syncing is a
                        // safe, correct fix — not just a workaround for this test.
                        LaunchedEffect(uid) {
                            app.financeRepository.seedIfEmpty()
                            app.shiftsRepository.seedIfEmpty()
                            app.shoppingRepository.seedIfEmpty()
                            app.debtRepository.seedIfEmpty()
                            app.financeSyncRepository.syncWalletsOnSignIn(uid)
                            app.shiftsSyncRepository.syncShiftTypesOnSignIn(uid)
                            app.shiftsSyncRepository.syncShiftDaysOnSignIn(uid)
                            app.categoriesSyncRepository.syncCategoriesOnSignIn(uid)
                            app.categoriesSyncRepository.syncSubcategoriesOnSignIn(uid)
                            app.budgetsSyncRepository.syncBudgetsOnSignIn(uid)
                            app.tagsSyncRepository.syncTagsOnSignIn(uid)
                            app.recurringSyncRepository.syncRecurringOnSignIn(uid)
                            app.transactionsSyncRepository.syncTransactionsOnSignIn(uid)
                            app.shoppingSyncRepository.syncShoppingListOnSignIn(uid)
                            app.debtSyncRepository.syncDebtsOnSignIn(uid)
                            // Mirrors js/color-picker.js's fbLoadNow() calling
                            // processRecurring() right after config/transactions load —
                            // materializes any recurring entries whose nextDate has
                            // fallen due into real transactions. Must run after both
                            // the recurring AND the transactions/wallets sync above, or
                            // it would compute against stale/empty local data.
                            app.financeRepository.processRecurring()
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
                        // Nullable initial value on purpose: collapsing "still reading
                        // DataStore" and "confirmed no PIN set" into the same `false`
                        // would flash the real app content for a frame before a PIN
                        // gate kicks in — a real gap for a security feature. Render
                        // nothing until the read actually completes.
                        val hasPin by pinViewModel.hasPin.collectAsState(initial = null)
                        when {
                            hasPin == null -> {}
                            hasPin == true && !pinViewModel.isUnlocked -> PinLockScreen(pinViewModel)
                            else -> RytmNavHost()
                        }
                    }
                }
            }
        }
    }
}
