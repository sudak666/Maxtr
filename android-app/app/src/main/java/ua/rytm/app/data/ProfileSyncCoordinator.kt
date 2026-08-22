package ua.rytm.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
        data class Error(val failure: SyncFailure) : RealtimeState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeSyncMutex = Mutex()
    private val _realtimeState = MutableStateFlow<RealtimeState>(RealtimeState.Stopped)
    val realtimeState = _realtimeState.asStateFlow()
    private var listeners = emptyList<ListenerRegistration>()
    private var pendingRealtimeSync: Job? = null
    private val pendingDomains = mutableSetOf<SyncDomain>()
    private var listenerGeneration = 0L

    private enum class SyncDomain { FINANCE, SHIFTS, DEBT, TRANSACTIONS }

    private fun publishFailure(error: Throwable) {
        val failure = SyncFailure.from(error)
        Log.w("RytmSync", failure.diagnosticCode)
        _realtimeState.value = RealtimeState.Error(failure)
    }

    private suspend fun syncFinanceDocumentDomains(uid: String, profileId: String) {
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
        app.shoppingSyncRepository.syncShoppingListOnSignIn(uid, profileId)
    }

    private suspend fun syncShiftsDocumentDomains(uid: String, profileId: String) {
        app.shiftsSyncRepository.syncShiftTypesOnSignIn(uid, profileId)
        app.shiftsSyncRepository.syncShiftDaysOnSignIn(uid, profileId)
        app.shiftsSyncRepository.syncAutoFillScheduleOnSignIn(uid, profileId)
    }

    // Every domain's cold sync against the given profile, plus recurring
    // materialization — same order MainActivity always ran these in.
    // Deliberately does NOT seed sample data — see switchProfile()'s own
    // comment for why a fresh non-default profile must never get demo
    // content pushed to it.
    private suspend fun syncAllDomains(uid: String, profileId: String) {
        syncFinanceDocumentDomains(uid, profileId)
        syncShiftsDocumentDomains(uid, profileId)
        app.transactionsSyncRepository.syncTransactionsOnSignIn(uid, profileId)
        app.debtSyncRepository.syncDebtsOnSignIn(uid, profileId)
        app.financeRepository.processRecurring()
        // Same "run the day-by-day catch-up once per cold sync" treatment as
        // processRecurring() above — the PWA re-checks on every visibility
        // change + a 5-minute interval (js/app-init.js), which this app has
        // no equivalent long-lived-tab lifecycle for; once per sign-in/
        // profile-switch is the honest Android analog, not a silent gap.
        if (app.shiftsRepository.processAutoFillShifts() > 0) {
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
        app.financeRepository.seedIfEmpty()
        app.shiftsRepository.seedIfEmpty()
        syncAllDomains(dataOwnerUid, profileId)
        startRealtimeSync(dataOwnerUid, profileId)
        return profileId
    }

    suspend fun retryLoad(uid: String): Boolean {
        _realtimeState.value = RealtimeState.Syncing
        return runCatching { loadOnSignIn(uid) }
            .fold(onSuccess = { true }, onFailure = { publishFailure(it); false })
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
        val generation = ++listenerGeneration
        val profileCollection = FirebaseFirestore.getInstance()
            .collection("users").document(ownerUid).collection("max_tracker")
        val finance = profileCollection.document(profileDocName("finance", profileId))
        val watched = listOf(
            finance,
            profileCollection.document(profileDocName("shifts", profileId)),
            profileCollection.document(profileDocName("debt", profileId)),
        )
        var initialSnapshotsRemaining = watched.size + 1
        var initialSnapshotWasOffline = false

        fun remoteChanged(error: Exception?, fromCache: Boolean, domain: SyncDomain) {
            if (generation != listenerGeneration) return
            if (error != null) {
                publishFailure(error)
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
            synchronized(pendingDomains) { pendingDomains += domain }
            pendingRealtimeSync?.cancel()
            pendingRealtimeSync = scope.launch {
                delay(250)
                realtimeSyncMutex.withLock {
                    if (generation != listenerGeneration) return@withLock
                    _realtimeState.value = RealtimeState.Syncing
                    val domains = synchronized(pendingDomains) { pendingDomains.toSet().also { pendingDomains.clear() } }
                    runCatching {
                        domains.forEach {
                            when (it) {
                                SyncDomain.FINANCE -> syncFinanceDocumentDomains(ownerUid, profileId)
                                SyncDomain.SHIFTS -> syncShiftsDocumentDomains(ownerUid, profileId)
                                SyncDomain.DEBT -> app.debtSyncRepository.syncDebtsOnSignIn(ownerUid, profileId)
                                SyncDomain.TRANSACTIONS -> app.transactionsSyncRepository.syncTransactionsOnSignIn(ownerUid, profileId)
                            }
                        }
                    }
                        .onSuccess { _realtimeState.value = RealtimeState.Listening }
                        .onFailure(::publishFailure)
                }
            }
        }

        listeners = watched.mapIndexed { index, ref ->
            ref.addSnapshotListener { snapshot, error ->
                if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
                remoteChanged(error, snapshot?.metadata?.isFromCache() == true, SyncDomain.entries[index])
            }
        } + finance.collection("transactions").addSnapshotListener { snapshot, error ->
            if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
            remoteChanged(error, snapshot?.metadata?.isFromCache() == true, SyncDomain.TRANSACTIONS)
        }
    }

    fun stopRealtimeSync() {
        listenerGeneration++
        pendingRealtimeSync?.cancel()
        pendingRealtimeSync = null
        synchronized(pendingDomains) { pendingDomains.clear() }
        listeners.forEach(ListenerRegistration::remove)
        listeners = emptyList()
        _realtimeState.value = RealtimeState.Stopped
    }
}
