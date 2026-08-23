package ua.rytm.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(
    entities = [
        WalletEntity::class, TransactionEntity::class, ShoppingItemEntity::class, CategoryEntity::class,
        ShiftTypeEntity::class, ShiftDayEntity::class, DebtEntity::class, DebtEntryEntity::class,
        SubcategoryEntity::class, BudgetEntity::class, TagEntity::class, RecurringEntity::class,
        CategoryIconEntity::class, GoalEntity::class, CurrencyRateEntity::class, AutoFillScheduleEntity::class, AutoRuleEntity::class,
        SyncOutboxEntity::class,
        SyncRevisionEntity::class,
    ],
    version = 19,
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
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncRevisionDao(): SyncRevisionDao
}

suspend fun RytmDatabase.adoptLegacyScope(ownerUid: String, profileId: String) = withTransaction {
    val args = arrayOf<Any>(ownerUid, profileId)
    PROFILE_TABLES.forEach { table ->
        openHelper.writableDatabase.execSQL(
            "UPDATE `$table` SET ownerUid = ?, profileId = ? WHERE ownerUid = ''",
            args,
        )
    }
}

internal val PROFILE_TABLES = listOf(
    "wallets", "transactions", "shopping_items", "categories", "shift_types", "shift_days",
    "debts", "debt_entries", "subcategories", "budgets", "tags", "recurring",
    "category_icons", "goals", "currency_rates", "autofill_schedule", "auto_rules",
)

// Active-profile reset keeps other offline profiles intact. Account removal
// and privacy-cache opt-out use clearAllProfileScopedTables() below instead.
suspend fun RytmDatabase.clearActiveProfileTables() {
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
    syncOutboxDao().clearScope(RoomProfileScope.ownerUid, RoomProfileScope.profileId)
    syncRevisionDao().clearScope(RoomProfileScope.ownerUid, RoomProfileScope.profileId)
}

/** Privacy-cache/account removal path: erases every retained offline profile. */
suspend fun RytmDatabase.clearAllProfileScopedTables() = withTransaction {
    PROFILE_TABLES.forEach { table -> openHelper.writableDatabase.execSQL("DELETE FROM `$table`") }
    openHelper.writableDatabase.execSQL("DELETE FROM `sync_outbox`")
    openHelper.writableDatabase.execSQL("DELETE FROM `sync_revisions`")
}
