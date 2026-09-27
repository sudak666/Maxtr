package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.local.clearAllProfileScopedTables

// Centralizes the "run every domain's cold sync against one profile" sequence
// — previously inlined directly in MainActivity's LaunchedEffect (steps
// 14-26), now shared between the normal sign-in load and an in-session
// profile switch (step 30) so the two paths can't silently drift apart.
// Room has no per-profile row-tagging (see RytmDatabase.clearAllProfileScopedTables's
// own doc comment), so switching profiles means starting the local cache
// over — mirrors the PWA's switchProfile() (fbSaveNow() the old profile,
// reassign activeProfileId, fbLoadNow() the new one), minus the local
// read-through cache the PWA has and this app doesn't.
class ProfileSyncCoordinator(private val app: RytmApplication) {
    sealed interface RealtimeState {
        data object Stopped : RealtimeState
        data object Listening : RealtimeState
        data object Syncing : RealtimeState
        data object Offline : RealtimeState
        data class Error(val message: String) : RealtimeState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeSyncMutex = Mutex()
    private val _realtimeState = MutableStateFlow<RealtimeState>(RealtimeState.Stopped)
    val realtimeState = _realtimeState.asStateFlow()
    private var listeners = emptyList<ListenerRegistration>()
    private var pendingRealtimeSync: Job? = null
    private var listenerGeneration = 0L
    // (ownerUid, profileId) the realtime listeners are currently attached to.
    // Lets loadOnSignIn() skip a full cold re-sync when the Activity is merely
    // recreated (theme/language change, rotation) — the application-scoped
    // listeners are still live and Room is already current.
    private var activeTarget: Pair<String, String>? = null

    // Every domain's cold sync against the given profile, plus recurring
    // materialization — same order MainActivity always ran these in.
    // Deliberately does NOT seed sample data — see switchProfile()'s own
    // comment for why a fresh non-default profile must never get demo
    // content pushed to it.
    /** Which watched Firestore source a realtime change came from. */
    enum class SyncDomain { FINANCE, TRANSACTIONS, SHIFTS, DEBT }

    private suspend fun syncAllDomains(uid: String, profileId: String) =
        syncDomains(uid, profileId, SyncDomain.entries.toSet())

    // Realtime changes re-sync only the domains whose source doc changed —
    // a shift edit used to re-read every finance domain and the whole
    // transactions subcollection too.
    private suspend fun syncDomains(uid: String, profileId: String, domains: Set<SyncDomain>) {
        if (SyncDomain.FINANCE in domains) {
            app.financeSyncRepository.syncWalletsOnSignIn(uid, profileId)
            app.categoriesSyncRepository.syncCategoriesOnSignIn(uid, profileId)
            app.categoriesSyncRepository.syncSubcategoriesOnSignIn(uid, profileId)
            app.categoriesSyncRepository.syncCategoryIconsOnSignIn(uid, profileId)
            app.budgetsSyncRepository.syncBudgetsOnSignIn(uid, profileId)
            app.tagsSyncRepository.syncTagsOnSignIn(uid, profileId)
            app.autoRulesSyncRepository.syncOnSignIn(uid, profileId)
            app.recurringSyncRepository.syncRecurringOnSignIn(uid, profileId)
            app.goalsSyncRepository.syncGoalsOnSignIn(uid, profileId)
            app.currencyRatesSyncRepository.syncCurrencyRatesOnSignIn(uid, profileId)
            app.widgetSettingsSyncRepository.syncOnSignIn(uid, profileId)
        }
        if (SyncDomain.SHIFTS in domains) {
            app.shiftsSyncRepository.syncShiftTypesOnSignIn(uid, profileId)
            app.shiftsSyncRepository.syncShiftDaysOnSignIn(uid, profileId)
            app.shiftsSyncRepository.syncAutoFillScheduleOnSignIn(uid, profileId)
        }
        if (SyncDomain.TRANSACTIONS in domains) app.transactionsSyncRepository.syncTransactionsOnSignIn(uid, profileId)
        if (SyncDomain.DEBT in domains) app.debtSyncRepository.syncDebtsOnSignIn(uid, profileId)
        if (SyncDomain.FINANCE in domains || SyncDomain.TRANSACTIONS in domains) {
            // Materialized payments must reach Firestore together with the advanced
            // nextDate — local-only, the next sync restored the old date from the
            // cloud and the same payment was created again (5× seen live).
            val created = app.financeRepository.processRecurring()
            if (created.isNotEmpty()) {
                app.transactionsSyncRepository.saveTransactions(uid, profileId, created)
                app.recurringSyncRepository.saveRecurringSnapshot(uid, profileId)
            }
        }
        // Same "run the day-by-day catch-up once per cold sync" treatment as
        // processRecurring() above — the PWA re-checks on every visibility
        // change + a 5-minute interval (js/app-init.js), which this app has
        // no equivalent long-lived-tab lifecycle for; once per sign-in/
        // profile-switch is the honest Android analog, not a silent gap.
        if (SyncDomain.SHIFTS in domains && app.shiftsRepository.processAutoFillShifts() > 0) {
            app.shiftsSyncRepository.saveShiftDays(uid, profileId)
        }
    }

    // Called once from MainActivity's sign-in LaunchedEffect — resolves
    // whichever profile this device was last on (defaults to the account's
    // own default profile) and loads it. Sample-data seeding stays here,
    // unconditional/idempotent exactly as it always was (each seedIfEmpty()
    // only acts on a genuinely empty table) — this is the one real "first
    // launch ever" path, unlike switchProfile() below. `dataOwnerUid`
    // (step 32) resolves to the sharer's uid when the last-active profile is
    // a joined shared one, else the signed-in account's own uid — every
    // Firestore path built downstream (users/{dataOwnerUid}/max_tracker/...)
    // needs this, not the signed-in uid, to actually reach the shared data.
    suspend fun loadOnSignIn(uid: String): String {
        val profileId = app.activeProfileStore.getActiveProfileId(uid)
        val dataOwnerUid = app.activeProfileStore.getActiveProfileOwnerUid(uid) ?: uid
        if (listeners.isNotEmpty() && activeTarget == (dataOwnerUid to profileId)) return profileId
        // Demo seeding only for the account's own default profile — same rule as
        // switchProfile(). On a restart into any other profile, an empty table
        // got demo shift types that the sync then wiped (seen live flashing in
        // the day picker, and a shift saved in that window was lost).
        if (profileId == DEFAULT_PROFILE_ID && dataOwnerUid == uid) {
            app.financeRepository.seedIfEmpty()
            app.shiftsRepository.seedIfEmpty()
        }
        syncAllDomains(dataOwnerUid, profileId)
        startRealtimeSync(dataOwnerUid, profileId)
        return profileId
    }

    // Mirrors js/color-picker.js's switchProfile(): flush-then-reload,
    // reassign the active profile, done. Never seeds sample data — a
    // genuinely fresh second profile (no remote docs of its own yet) must
    // start truly empty like it would on the PWA, not receive this device's
    // demo wallets/transactions pushed to Firestore as if they were real
    // content for that profile (a mistake unique to profile-switching: the
    // very first sync call's "no remote doc yet -> push local as seed"
    // branch would otherwise fire against genuinely fake data). `dataOwnerUid`
    // (step 32) is non-null when switching into a shared profile someone
    // else owns — persisted via ActiveProfileStore.setActiveProfile() so a
    // restart resolves the same owner without a second profiles_meta lookup.
    suspend fun switchProfile(uid: String, newProfileId: String, dataOwnerUid: String? = null) {
        stopRealtimeSync()
        app.database.clearAllProfileScopedTables()
        app.activeProfileStore.setActiveProfile(uid, newProfileId, dataOwnerUid)
        val ownerUid = dataOwnerUid ?: uid
        syncAllDomains(ownerUid, newProfileId)
        startRealtimeSync(ownerUid, newProfileId)
    }

    // Mirrors the PWA's resetProfileData(): deletes only the active own
    // profile's data, keeps the Firebase account/profile metadata, clears the
    // local cache, then recreates the same fresh defaults used on first launch.
    // Shared ownership is rejected again here, not only by the UI.
    suspend fun resetOwnProfile(uid: String, profileId: String, activeProfileOwnerUid: String?) {
        require(activeProfileOwnerUid == null) { "Shared profiles cannot be reset" }
        val profileCollection = FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("max_tracker")
        val financeRef = profileCollection.document(profileDocName("finance", profileId))
        val transactions = financeRef.collection("transactions").get().await().documents
        transactions.chunked(450).forEach { chunk ->
            val batch = FirebaseFirestore.getInstance().batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        listOf("shifts", "finance", "debt").forEach { baseName ->
            profileCollection.document(profileDocName(baseName, profileId)).delete().await()
        }

        app.database.clearAllProfileScopedTables()
        app.financeRepository.seedFreshProfileDefaults()
        app.shiftsRepository.seedFreshProfileDefaults()
        syncAllDomains(uid, profileId)
    }

    /** Keeps Room current when another signed-in client changes the active profile. */
    fun startRealtimeSync(ownerUid: String, profileId: String) {
        stopRealtimeSync()
        activeTarget = ownerUid to profileId
        val generation = ++listenerGeneration
        val profileCollection = FirebaseFirestore.getInstance()
            .collection("users").document(ownerUid).collection("max_tracker")
        val finance = profileCollection.document(profileDocName("finance", profileId))
        val watched = listOf(
            finance to SyncDomain.FINANCE,
            profileCollection.document(profileDocName("shifts", profileId)) to SyncDomain.SHIFTS,
            profileCollection.document(profileDocName("debt", profileId)) to SyncDomain.DEBT,
        )
        // Domains changed since the last realtime sync started; accumulated so a
        // debounce-cancelled job never drops a domain a newer change didn't touch.
        val dirty = mutableSetOf<SyncDomain>()
        var initialSnapshotsRemaining = watched.size + 1
        var initialSnapshotWasOffline = false

        fun remoteChanged(domain: SyncDomain, error: Exception?, fromCache: Boolean) {
            if (generation != listenerGeneration) return
            if (error != null) {
                _realtimeState.value = RealtimeState.Error(error.message ?: "Realtime sync failed")
                return
            }
            if (initialSnapshotsRemaining > 0) {
                initialSnapshotWasOffline = initialSnapshotWasOffline || fromCache
                initialSnapshotsRemaining--
                if (initialSnapshotsRemaining == 0) {
                    _realtimeState.value = if (initialSnapshotWasOffline) RealtimeState.Offline else RealtimeState.Listening
                }
                return
            }
            if (fromCache) {
                _realtimeState.value = RealtimeState.Offline
                return
            }
            synchronized(dirty) { dirty += domain }
            pendingRealtimeSync?.cancel()
            pendingRealtimeSync = scope.launch {
                delay(250)
                realtimeSyncMutex.withLock {
                    if (generation != listenerGeneration) return@withLock
                    val domains = synchronized(dirty) { dirty.toSet().also { dirty.clear() } }
                    if (domains.isEmpty()) return@withLock
                    _realtimeState.value = RealtimeState.Syncing
                    runCatching { syncDomains(ownerUid, profileId, domains) }
                        .onSuccess { _realtimeState.value = RealtimeState.Listening }
                        .onFailure {
                            // A sync cancelled by stopRealtimeSync()/a newer change is not
                            // a failure — reporting it flashed a false "sync error" banner.
                            if (generation != listenerGeneration) return@onFailure
                            if (it is CancellationException) {
                                synchronized(dirty) { dirty += domains } // retried by the newer job
                                return@onFailure
                            }
                            _realtimeState.value = RealtimeState.Error(it.message ?: "Realtime sync failed")
                        }
                }
            }
        }

        listeners = watched.map { (ref, domain) ->
            ref.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
                remoteChanged(domain, error, snapshot?.metadata?.isFromCache() == true)
            }
        } + finance.collection("transactions").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
            remoteChanged(SyncDomain.TRANSACTIONS, error, snapshot?.metadata?.isFromCache() == true)
        }
    }

    fun stopRealtimeSync() {
        listenerGeneration++
        pendingRealtimeSync?.cancel()
        pendingRealtimeSync = null
        listeners.forEach(ListenerRegistration::remove)
        listeners = emptyList()
        activeTarget = null
        _realtimeState.value = RealtimeState.Stopped
    }
}
