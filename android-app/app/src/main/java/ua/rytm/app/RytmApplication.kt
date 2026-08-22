package ua.rytm.app

import android.app.Application
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import ua.rytm.app.data.BudgetsSyncRepository
import ua.rytm.app.data.AutoRulesSyncRepository
import ua.rytm.app.data.CategoriesSyncRepository
import ua.rytm.app.data.CurrencyRatesSyncRepository
import ua.rytm.app.data.MonobankRepository
import ua.rytm.app.data.WidgetSettingsSyncRepository
import ua.rytm.app.data.DebtRepository
import ua.rytm.app.data.DebtSyncRepository
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.FinanceSyncRepository
import ua.rytm.app.data.GoalsSyncRepository
import ua.rytm.app.data.RecurringSyncRepository
import ua.rytm.app.data.TransactionsSyncRepository
import ua.rytm.app.data.ShiftsRepository
import ua.rytm.app.data.ShiftsSyncRepository
import ua.rytm.app.data.ShoppingRepository
import ua.rytm.app.data.ShoppingSyncRepository
import ua.rytm.app.data.TagsSyncRepository
import ua.rytm.app.data.PushRepository
import ua.rytm.app.data.ProfileSyncCoordinator
import ua.rytm.app.data.ProfileAppearanceRepository
import ua.rytm.app.data.ProfilesRepository
import ua.rytm.app.data.local.ActiveProfileStore
import ua.rytm.app.data.local.PinStore
import ua.rytm.app.data.local.MonobankTokenStore
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.RytmMigrations
import ua.rytm.app.data.local.SettingsStore
import ua.rytm.app.push.ensureNotificationChannel
import ua.rytm.app.work.scheduleDailyMaintenance
import ua.rytm.app.work.scheduleSyncOutbox

class RytmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        val appCheckProvider = if (BuildConfig.DEBUG) {
            runCatching {
                val type = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                type.getMethod("getInstance").invoke(null) as AppCheckProviderFactory
            }.getOrElse { PlayIntegrityAppCheckProviderFactory.getInstance() }
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProvider)
        // See build.gradle.kts's USE_FIREBASE_EMULATOR comment — off by default,
        // only set by an explicit -PuseFirebaseEmulator=true build. Must run before
        // anything touches FirebaseAuth/FirebaseFirestore (both below are `by lazy`,
        // so this is safe as long as onCreate() runs first, which Application
        // guarantees). 10.0.2.2 is the Android emulator's alias for the host
        // machine's localhost.
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // "127.0.0.1", not the usual AVD host-alias "10.0.2.2" — this environment's
            // Windows Firewall silently drops inbound connections to the emulator ports
            // from the AVD's virtual subnet (a real, unprivileged constraint: creating a
            // firewall rule needs admin rights this session doesn't have and shouldn't
            // take). `adb reverse tcp:8080 tcp:8080`/`tcp:9099 tcp:9099` tunnels the
            // device's own localhost through the already-established ADB connection
            // instead, sidestepping the firewall question entirely.
            FirebaseAuth.getInstance().useEmulator("127.0.0.1", 9099)
            FirebaseFirestore.getInstance().useEmulator("127.0.0.1", 8080)
        }
        // Created unconditionally at process start, not lazily from Settings —
        // the system's own auto-display path for a backgrounded push (see
        // RytmMessagingService's doc comment) needs the channel to already
        // exist the first time a push arrives, which can happen before the
        // user ever opens Settings on a device that registered a token on a
        // previous install/run. NotificationManager.createNotificationChannel()
        // is a safe no-op when the channel already exists (same id, same
        // settings), so calling this on every cold start is fine.
        ensureNotificationChannel(this)
        scheduleDailyMaintenance(this)
        scheduleSyncOutbox(this)
    }

    val database: RytmDatabase by lazy {
        // Pre-launch, no real users yet (CLAUDE.md convention) — a destructive
        // fallback across local schema bumps is fine; there's no user data to
        // protect through a real migration path.
        Room.databaseBuilder(this, RytmDatabase::class.java, "rytm.db")
            .addMigrations(*RytmMigrations.ALL)
            .build()
    }
    val financeRepository: FinanceRepository by lazy { FinanceRepository(database) }
    val shoppingRepository: ShoppingRepository by lazy { ShoppingRepository(database) }
    val shiftsRepository: ShiftsRepository by lazy { ShiftsRepository(database, shiftsSyncRepository) }
    val debtRepository: DebtRepository by lazy { DebtRepository(database) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val pinStore: PinStore by lazy { PinStore(this) }
    val financeSyncRepository: FinanceSyncRepository by lazy { FinanceSyncRepository(database, FirebaseFirestore.getInstance()) }
    val shiftsSyncRepository: ShiftsSyncRepository by lazy { ShiftsSyncRepository(database, FirebaseFirestore.getInstance()) }
    val categoriesSyncRepository: CategoriesSyncRepository by lazy { CategoriesSyncRepository(database, FirebaseFirestore.getInstance()) }
    val transactionsSyncRepository: TransactionsSyncRepository by lazy { TransactionsSyncRepository(database, FirebaseFirestore.getInstance(), this) }
    val shoppingSyncRepository: ShoppingSyncRepository by lazy { ShoppingSyncRepository(database, FirebaseFirestore.getInstance(), this) }
    val debtSyncRepository: DebtSyncRepository by lazy { DebtSyncRepository(database, FirebaseFirestore.getInstance()) }
    val budgetsSyncRepository: BudgetsSyncRepository by lazy { BudgetsSyncRepository(database, FirebaseFirestore.getInstance()) }
    val tagsSyncRepository: TagsSyncRepository by lazy { TagsSyncRepository(database, FirebaseFirestore.getInstance()) }
    val recurringSyncRepository: RecurringSyncRepository by lazy { RecurringSyncRepository(database, FirebaseFirestore.getInstance()) }
    val goalsSyncRepository: GoalsSyncRepository by lazy { GoalsSyncRepository(database, FirebaseFirestore.getInstance()) }
    val currencyRatesSyncRepository: CurrencyRatesSyncRepository by lazy { CurrencyRatesSyncRepository(database, FirebaseFirestore.getInstance()) }
    val monobankRepository: MonobankRepository by lazy { MonobankRepository(database, FirebaseFirestore.getInstance(), FirebaseAuth.getInstance(), MonobankTokenStore(this)) }
    val widgetSettingsSyncRepository: WidgetSettingsSyncRepository by lazy { WidgetSettingsSyncRepository(settingsStore, FirebaseFirestore.getInstance()) }
    val autoRulesSyncRepository: AutoRulesSyncRepository by lazy { AutoRulesSyncRepository(database, FirebaseFirestore.getInstance()) }
    val pushRepository: PushRepository by lazy { PushRepository(FirebaseFirestore.getInstance()) }
    val activeProfileStore: ActiveProfileStore by lazy { ActiveProfileStore(this) }
    val profilesRepository: ProfilesRepository by lazy { ProfilesRepository(FirebaseFirestore.getInstance()) }
    val profileAppearanceRepository: ProfileAppearanceRepository by lazy { ProfileAppearanceRepository(FirebaseFirestore.getInstance()) }
    val profileSyncCoordinator: ProfileSyncCoordinator by lazy { ProfileSyncCoordinator(this) }
}
