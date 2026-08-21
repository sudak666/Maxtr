package ua.rytm.app.data

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

    // Every domain's cold sync against the given profile, plus recurring
    // materialization — same order MainActivity always ran these in.
    // Deliberately does NOT seed sample data — see switchProfile()'s own
    // comment for why a fresh non-default profile must never get demo
    // content pushed to it.
    private suspend fun syncAllDomains(uid: String, profileId: String) {
        app.financeSyncRepository.syncWalletsOnSignIn(uid, profileId)
        app.shiftsSyncRepository.syncShiftTypesOnSignIn(uid, profileId)
        app.shiftsSyncRepository.syncShiftDaysOnSignIn(uid, profileId)
        app.categoriesSyncRepository.syncCategoriesOnSignIn(uid, profileId)
        app.categoriesSyncRepository.syncSubcategoriesOnSignIn(uid, profileId)
        app.categoriesSyncRepository.syncCategoryIconsOnSignIn(uid, profileId)
        app.budgetsSyncRepository.syncBudgetsOnSignIn(uid, profileId)
        app.tagsSyncRepository.syncTagsOnSignIn(uid, profileId)
        app.recurringSyncRepository.syncRecurringOnSignIn(uid, profileId)
        app.transactionsSyncRepository.syncTransactionsOnSignIn(uid, profileId)
        app.shoppingSyncRepository.syncShoppingListOnSignIn(uid, profileId)
        app.debtSyncRepository.syncDebtsOnSignIn(uid, profileId)
        app.financeRepository.processRecurring()
    }

    // Called once from MainActivity's sign-in LaunchedEffect — resolves
    // whichever profile this device was last on (defaults to the account's
    // own default profile) and loads it. Sample-data seeding stays here,
    // unconditional/idempotent exactly as it always was (each seedIfEmpty()
    // only acts on a genuinely empty table) — this is the one real "first
    // launch ever" path, unlike switchProfile() below.
    suspend fun loadOnSignIn(uid: String): String {
        val profileId = app.activeProfileStore.getActiveProfileId(uid)
        app.financeRepository.seedIfEmpty()
        app.shiftsRepository.seedIfEmpty()
        app.shoppingRepository.seedIfEmpty()
        app.debtRepository.seedIfEmpty()
        syncAllDomains(uid, profileId)
        return profileId
    }

    // Mirrors js/color-picker.js's switchProfile(): flush-then-reload,
    // reassign the active profile, done. Never seeds sample data — a
    // genuinely fresh second profile (no remote docs of its own yet) must
    // start truly empty like it would on the PWA, not receive this device's
    // demo wallets/transactions pushed to Firestore as if they were real
    // content for that profile (a mistake unique to profile-switching: the
    // very first sync call's "no remote doc yet -> push local as seed"
    // branch would otherwise fire against genuinely fake data).
    suspend fun switchProfile(uid: String, newProfileId: String) {
        app.database.clearAllProfileScopedTables()
        app.activeProfileStore.setActiveProfileId(uid, newProfileId)
        syncAllDomains(uid, newProfileId)
    }
}
