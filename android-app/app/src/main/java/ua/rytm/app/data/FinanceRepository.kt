package ua.rytm.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.rytm.app.data.local.BudgetEntity
import ua.rytm.app.data.local.CategoryEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.SubcategoryEntity
import ua.rytm.app.ui.screens.finance.SampleFinanceData
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.Wallet

// Mirrors js/core.js's subKey(type,name) => `${type}:${name}` — the PWA's own
// composite key for AppState.subcategories, since a category name alone isn't
// unique across income/expense.
fun subKey(type: String, name: String) = "$type:$name"

// Real persistence backing FinanceViewModel — see ANDROID_MIGRATION.md §2/§7
// and FINANCE_SCREEN_SPEC.md §8 for what this replaces (the old in-memory
// SampleFinanceData-only state). seedIfEmpty() uses SampleFinanceData as
// bootstrap content for a genuinely-empty local Room table, harmlessly
// overwritten by the real Firestore cold-sync that runs right after (see
// MainActivity's LaunchedEffect) for any domain that has one.
class FinanceRepository(private val db: RytmDatabase) {

    val wallets: Flow<List<Wallet>> = db.walletDao().observeAll().map { list -> list.map { it.toDomain() } }
    val transactions: Flow<List<Transaction>> = db.transactionDao().observeAll().map { list -> list.map { it.toDomain() } }

    /** name -> TxType, per category — mirrors AppState.categories[type] (js/state.js). */
    val categoriesByType: Flow<Map<TxType, List<String>>> = db.categoryDao().observeAll().map { list ->
        list.groupBy { TxType.valueOf(it.type) }.mapValues { (_, entities) -> entities.map { it.name } }
    }

    /** id+name pairs, for the manager screen (delete needs the id; the tx form only needs names). */
    val categoryEntriesByType: Flow<Map<TxType, List<Pair<String, String>>>> = db.categoryDao().observeAll().map { list ->
        list.groupBy { TxType.valueOf(it.type) }.mapValues { (_, entities) -> entities.map { it.id to it.name } }
    }

    /** subKey(type,name) -> subcategory names — mirrors AppState.subcategories (js/core.js's subKey()). */
    val subcategoriesByKey: Flow<Map<String, List<String>>> = db.subcategoryDao().observeAll().map { list ->
        list.groupBy { subKey(it.categoryType, it.categoryName) }.mapValues { (_, entities) -> entities.map { it.name } }
    }

    /** expense category name -> monthly limit — mirrors AppState.budgets (js/state.js). */
    val budgets: Flow<Map<String, Double>> = db.budgetDao().observeAll().map { list -> list.associate { it.category to it.amount } }

    suspend fun seedIfEmpty() {
        if (db.walletDao().count() == 0) {
            db.walletDao().insertAll(SampleFinanceData.wallets.map { it.toEntity() })
        }
        if (db.transactionDao().count() == 0) {
            db.transactionDao().insertAll(SampleFinanceData.transactions.mapIndexed { index, tx -> tx.toEntity(createdAt = index.toLong()) })
        }
        if (db.categoryDao().count() == 0) {
            val seed = SampleFinanceData.incomeCategories.map { CategoryEntity(id = java.util.UUID.randomUUID().toString(), type = TxType.INCOME.name, name = it) } +
                SampleFinanceData.expenseCategories.map { CategoryEntity(id = java.util.UUID.randomUUID().toString(), type = TxType.EXPENSE.name, name = it) }
            db.categoryDao().insertAll(seed)
        }
        // SampleFinanceData.subcategories is keyed by name only (all its entries
        // happen to be expense categories) — EXPENSE is the real type here, not
        // a guess, since js/state.js's real DEFAULT_CATEGORIES has no default
        // subcategories to seed from at all (this is illustrative sample content).
        if (db.subcategoryDao().count() == 0) {
            val seed = SampleFinanceData.subcategories.flatMap { (categoryName, names) ->
                names.map { SubcategoryEntity(categoryType = TxType.EXPENSE.name, categoryName = categoryName, name = it) }
            }
            db.subcategoryDao().insertAll(seed)
        }
    }

    /** Mirrors addCategory()'s duplicate-name guard in js/settings-managers.js. Returns false if the name already exists for that type. */
    suspend fun addCategory(type: TxType, name: String): Boolean {
        if (db.categoryDao().countByTypeAndName(type.name, name) > 0) return false
        db.categoryDao().insert(CategoryEntity(id = java.util.UUID.randomUUID().toString(), type = type.name, name = name))
        return true
    }

    // Cascades the rename into subcategories AND budgets — mirrors
    // js/settings-managers.js's renameCategory() moving
    // AppState.subcategories[subKey(type,oldName)]/AppState.budgets[oldName]
    // to the new key.
    suspend fun renameCategory(id: String, type: TxType, newName: String) {
        val old = db.categoryDao().getById(id)
        db.categoryDao().insert(CategoryEntity(id = id, type = type.name, name = newName))
        if (old != null && old.name != newName) {
            db.subcategoryDao().renameCategoryName(type.name, old.name, newName)
            db.budgetDao().renameCategory(old.name, newName)
        }
    }

    // Cascades the delete into subcategories AND budgets — mirrors
    // js/settings-managers.js's deleteCategory() cascade (see CategoryEntity's
    // own doc comment, updated now that this cascade is actually implemented).
    suspend fun deleteCategory(id: String) {
        val category = db.categoryDao().getById(id)
        db.categoryDao().deleteById(id)
        if (category != null) {
            db.subcategoryDao().deleteAllForCategory(category.type, category.name)
            db.budgetDao().deleteByCategory(category.name)
        }
    }

    /** Mirrors addSubcategory()'s duplicate-name guard in js/settings-managers.js. Returns false if the name already exists under that category. */
    suspend fun addSubcategory(type: TxType, categoryName: String, name: String): Boolean {
        if (db.subcategoryDao().countOne(type.name, categoryName, name) > 0) return false
        db.subcategoryDao().insert(SubcategoryEntity(categoryType = type.name, categoryName = categoryName, name = name))
        return true
    }

    suspend fun deleteSubcategory(type: TxType, categoryName: String, name: String) {
        db.subcategoryDao().deleteOne(type.name, categoryName, name)
    }

    // Mirrors js/settings-managers.js's updateBudget(): a limit <=0 removes the
    // row entirely rather than being stored as a zero-or-negative value.
    suspend fun setBudget(category: String, amount: Double) {
        if (amount <= 0) db.budgetDao().deleteByCategory(category) else db.budgetDao().upsert(BudgetEntity(category, amount))
    }

    suspend fun upsertTransaction(transaction: Transaction) {
        db.transactionDao().upsert(transaction.toEntity())
    }

    suspend fun deleteTransaction(id: String) {
        db.transactionDao().deleteById(id)
    }

    suspend fun addWallet(wallet: Wallet) {
        db.walletDao().insert(wallet.toEntity())
    }

    suspend fun updateWallet(wallet: Wallet) {
        db.walletDao().update(wallet.toEntity())
    }

    /** Mirrors walletInUse() in js/settings-managers.js — recurring isn't ported yet, so only transactions are checked. */
    suspend fun isWalletInUse(id: String): Boolean = db.transactionDao().countUsingWallet(id) > 0

    suspend fun walletCount(): Int = db.walletDao().count()

    suspend fun deleteWallet(id: String) {
        db.walletDao().deleteById(id)
    }
}
