package ua.rytm.app.data

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
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.adoptLegacyScope
import ua.rytm.app.data.local.clearActiveProfileTables

// Centralizes every domain's sync sequence for sign-in and profile switching.
// Room v16 retains each owner/profile independently; switching the selector
// immediately rebinds repository Flows and then refreshes that scope remotely.
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
        SafeDiagnostics.reportSync(SafeDiagnostics.Domain.PROFILE, failure)
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

    // Syncs one scope without seeding demo content into secondary profiles.
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

    // Resolves the last active scope. Shared profiles sync against their owner;
    // first-launch seed functions remain idempotent.
    suspend fun loadOnSignIn(uid: String): String {
        val profileId = app.activeProfileStore.getActiveProfileId(uid)
        val dataOwnerUid = app.activeProfileStore.getActiveProfileOwnerUid(uid) ?: uid
        RoomProfileScope.activate(dataOwnerUid, profileId)
        app.database.adoptLegacyScope(dataOwnerUid, profileId)
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

    // Secondary profiles start empty; shared scopes persist their data owner
    // so restart does not require another metadata lookup.
    suspend fun switchProfile(uid: String, newProfileId: String, dataOwnerUid: String? = null) {
        stopRealtimeSync()
        app.activeProfileStore.setActiveProfile(uid, newProfileId, dataOwnerUid)
        val ownerUid = dataOwnerUid ?: uid
        RoomProfileScope.activate(ownerUid, newProfileId)
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

        app.database.clearActiveProfileTables()
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
