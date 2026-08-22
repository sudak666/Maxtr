package ua.rytm.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WalletEntity::class, TransactionEntity::class, ShoppingItemEntity::class, CategoryEntity::class,
        ShiftTypeEntity::class, ShiftDayEntity::class, DebtEntity::class, DebtEntryEntity::class,
        SubcategoryEntity::class, BudgetEntity::class, TagEntity::class, RecurringEntity::class,
        CategoryIconEntity::class, GoalEntity::class, CurrencyRateEntity::class, AutoFillScheduleEntity::class, AutoRuleEntity::class,
    ],
    version = 15,
    exportSchema = true,
)
abstract class RytmDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun transactionDao(): TransactionDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shiftTypeDao(): ShiftTypeDao
    abstract fun shiftDayDao(): ShiftDayDao
    abstract fun autoFillScheduleDao(): AutoFillScheduleDao
    abstract fun debtDao(): DebtDao
    abstract fun debtEntryDao(): DebtEntryDao
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun tagDao(): TagDao
    abstract fun recurringDao(): RecurringDao
    abstract fun categoryIconDao(): CategoryIconDao
    abstract fun goalDao(): GoalDao
    abstract fun currencyRateDao(): CurrencyRateDao
    abstract fun autoRuleDao(): AutoRuleDao
}

// Every local table this app persists is scoped to whichever profile was
// last cold-synced — Room has no per-profile row-tagging (unlike Firestore's
// per-doc-name suffixing, see ProfileDocNames.kt), so switching the active
// profile means starting the local cache over: wipe every table, then let
// the normal cold-sync sequence (MainActivity's LaunchedEffect, or
// ProfileSwitcher for an in-session switch) repopulate it from the new
// profile's own Firestore docs. Not run on every app launch — only when the
// active profile actually changes, mirroring the PWA's switchProfile()
// (fbSaveNow() the old profile, reassign activeProfileId, fbLoadNow() the
// new one) minus the local read-through cache the PWA has and Android
// doesn't.
suspend fun RytmDatabase.clearAllProfileScopedTables() {
    walletDao().clearAll()
    transactionDao().clearAll()
    shoppingDao().clearAll()
    categoryDao().clearAll()
    shiftTypeDao().clearAll()
    shiftDayDao().clearAll()
    debtDao().clearAll()
    debtEntryDao().clearAll()
    subcategoryDao().clearAll()
    budgetDao().clearAll()
    tagDao().clearAll()
    recurringDao().clearAll()
    categoryIconDao().clearAll()
    goalDao().clearAll()
    currencyRateDao().clearAll()
    autoFillScheduleDao().clearAll()
    autoRuleDao().clearAll()
}
