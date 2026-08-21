package ua.rytm.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.rytm.app.data.local.BudgetEntity
import ua.rytm.app.data.local.CategoryEntity
import ua.rytm.app.data.local.GoalEntity
import ua.rytm.app.data.local.RecurringEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.SubcategoryEntity
import ua.rytm.app.data.local.TagEntity
import ua.rytm.app.data.local.TransactionEntity
import ua.rytm.app.ui.screens.finance.Goal
import ua.rytm.app.ui.screens.finance.Recurring
import ua.rytm.app.ui.screens.finance.SampleFinanceData
import ua.rytm.app.ui.screens.finance.Tag
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.Wallet

// Mirrors js/core.js's SEED_RATES — the fallback used when a currency code
// has no synced rate yet (a brand-new account before its first FX-widget
// load, or a currency the account owner never manually rated).
val SEED_RATES = mapOf("USD" to 41.0, "EUR" to 44.0, "GBP" to 51.0, "PLN" to 10.5)

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

    /** mirrors AppState.tags (js/state.js). */
    val tags: Flow<List<Tag>> = db.tagDao().observeAll().map { list -> list.map { Tag(it.id, it.name, it.colorHex) } }

    /** category name -> manual icon-name override — mirrors AppState.categoryIcons (js/state.js). */
    val categoryIcons: Flow<Map<String, String>> = db.categoryIconDao().observeAll().map { list -> list.associate { it.categoryName to it.iconName } }

    /** mirrors AppState.recurring (js/state.js). */
    val recurring: Flow<List<Recurring>> = db.recurringDao().observeAll().map { list -> list.map { it.toDomain() } }

    /** mirrors AppState.goals (js/state.js). */
    val goals: Flow<List<Goal>> = db.goalDao().observeAll().map { list -> list.map { Goal(it.id, it.walletId, it.targetAmount, it.targetDate) } }

    /** currency code -> rate to UAH — mirrors AppState.currencyRates (js/core.js). */
    val currencyRates: Flow<Map<String, Double>> = db.currencyRateDao().observeAll().map { list -> list.associate { it.code to it.rateToUah } }

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

    // Cascades the rename into subcategories, budgets, recurring AND
    // categoryIcons — mirrors js/settings-managers.js's renameCategory()
    // moving AppState.subcategories[subKey(type,oldName)]/
    // AppState.budgets[oldName]/every AppState.recurring entry of this
    // type+name/AppState.categoryIcons[oldName] to the new key.
    suspend fun renameCategory(id: String, type: TxType, newName: String) {
        val old = db.categoryDao().getById(id)
        db.categoryDao().insert(CategoryEntity(id = id, type = type.name, name = newName))
        if (old != null && old.name != newName) {
            db.subcategoryDao().renameCategoryName(type.name, old.name, newName)
            db.budgetDao().renameCategory(old.name, newName)
            db.recurringDao().renameCategory(type.name, old.name, newName)
            db.categoryIconDao().renameCategory(old.name, newName)
        }
    }

    // Cascades the delete into subcategories, budgets AND categoryIcons —
    // mirrors js/settings-managers.js's deleteCategory() cascade (see
    // CategoryEntity's own doc comment, updated now that this cascade is
    // actually implemented). Deliberately does NOT cascade into recurring —
    // confirmed by reading deleteCategory() itself, which only ever touches
    // budgets/subcategories/categoryIcons, never AppState.recurring (only
    // renameCategory() reaches that far — see RecurringEntities.kt's own
    // doc comment for the same distinction already made there).
    suspend fun deleteCategory(id: String) {
        val category = db.categoryDao().getById(id)
        db.categoryDao().deleteById(id)
        if (category != null) {
            db.subcategoryDao().deleteAllForCategory(category.type, category.name)
            db.budgetDao().deleteByCategory(category.name)
            db.categoryIconDao().deleteForCategory(category.name)
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

    // Mirrors js/settings-managers.js's selectCategoryIcon(): sets a manual
    // icon override for the given category name.
    suspend fun setCategoryIcon(categoryName: String, iconName: String) {
        db.categoryIconDao().insert(ua.rytm.app.data.local.CategoryIconEntity(categoryName, iconName))
    }

    // Mirrors js/settings-managers.js's updateBudget(): a limit <=0 removes the
    // row entirely rather than being stored as a zero-or-negative value.
    suspend fun setBudget(category: String, amount: Double) {
        if (amount <= 0) db.budgetDao().deleteByCategory(category) else db.budgetDao().upsert(BudgetEntity(category, amount))
    }

    // Mirrors js/finance.js's addTag() — color picked by rotating PALETTE, same
    // as ShiftTypeDao's own creation convention (no interactive color picker yet).
    suspend fun addTag(name: String, colorHex: Long) {
        db.tagDao().insert(TagEntity(id = java.util.UUID.randomUUID().toString(), name = name, colorHex = colorHex))
    }

    suspend fun renameTag(id: String, newName: String, colorHex: Long) {
        db.tagDao().update(TagEntity(id = id, name = newName, colorHex = colorHex))
    }

    // Mirrors js/finance.js's deleteTag(): strips the id from every transaction
    // that referenced it, not just the tags table row itself.
    suspend fun deleteTag(id: String) {
        db.tagDao().deleteById(id)
        db.transactionDao().getAllOnce().forEach { tx ->
            if (tx.tags.split(",").contains(id)) {
                val remaining = tx.tags.split(",").filter { it.isNotBlank() && it != id }
                db.transactionDao().upsert(tx.copy(tags = remaining.joinToString(",")))
            }
        }
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

    // Mirrors walletInUse() in js/settings-managers.js: `AppState.transactions.some(t=>
    // t.wallet===id||t.targetWallet===id) || AppState.recurring.some(r=>r.wallet===id)`.
    // Was transactions-only before recurring was ported (step 24's predecessor left this
    // disclosed) — now checks both, same as the PWA.
    suspend fun isWalletInUse(id: String): Boolean = db.transactionDao().countUsingWallet(id) > 0 || db.recurringDao().countUsingWallet(id) > 0

    suspend fun walletCount(): Int = db.walletDao().count()

    suspend fun deleteWallet(id: String) {
        db.walletDao().deleteById(id)
    }

    // Mirrors js/settings-managers.js's addRecurring(): defaults to type=expense,
    // first expense category, first wallet, monthly, nextDate=today, active, amount=0.
    suspend fun addRecurring() {
        val expenseCategory = db.categoryDao().getAllOnce().firstOrNull { it.type == TxType.EXPENSE.name }?.name ?: "Інше"
        val walletId = db.walletDao().getAllOnce().firstOrNull()?.id ?: ""
        db.recurringDao().insert(
            RecurringEntity(
                id = java.util.UUID.randomUUID().toString(),
                type = TxType.EXPENSE.name,
                amount = 0.0,
                category = expenseCategory,
                walletId = walletId,
                frequency = "monthly",
                nextDate = java.time.LocalDate.now().toString(),
                active = true,
                comment = "",
            ),
        )
    }

    suspend fun deleteRecurring(id: String) {
        db.recurringDao().deleteById(id)
    }

    suspend fun updateRecurringAmount(recurring: Recurring, amount: Double) {
        db.recurringDao().update(recurring.copy(amount = amount).toEntity())
    }

    // Mirrors updateRecurring()'s 'type' branch (js/settings-managers.js): switching
    // type resets category to the first category of the new type, same reasoning as
    // TransactionFormSheet's own type-switch category reset.
    suspend fun updateRecurringType(recurring: Recurring, type: TxType) {
        val newCategory = db.categoryDao().getAllOnce().firstOrNull { it.type == type.name }?.name ?: "Інше"
        db.recurringDao().update(recurring.copy(type = type, category = newCategory).toEntity())
    }

    suspend fun updateRecurringCategory(recurring: Recurring, category: String) {
        db.recurringDao().update(recurring.copy(category = category).toEntity())
    }

    suspend fun updateRecurringWallet(recurring: Recurring, walletId: String) {
        db.recurringDao().update(recurring.copy(walletId = walletId).toEntity())
    }

    suspend fun updateRecurringFrequency(recurring: Recurring, frequency: String) {
        db.recurringDao().update(recurring.copy(frequency = frequency).toEntity())
    }

    suspend fun updateRecurringNextDate(recurring: Recurring, nextDate: String) {
        db.recurringDao().update(recurring.copy(nextDate = nextDate).toEntity())
    }

    suspend fun updateRecurringActive(recurring: Recurring, active: Boolean) {
        db.recurringDao().update(recurring.copy(active = active).toEntity())
    }

    // Mirrors js/goals-profile.js's confirmAddGoal(): defaults to the first
    // wallet, target 0, no date — the PWA's own new-goal form starts blank too.
    suspend fun addGoal() {
        val walletId = db.walletDao().getAllOnce().firstOrNull()?.id ?: return
        db.goalDao().insert(GoalEntity(id = java.util.UUID.randomUUID().toString(), walletId = walletId, targetAmount = 0.0, targetDate = ""))
    }

    suspend fun deleteGoal(id: String) {
        db.goalDao().deleteById(id)
    }

    suspend fun updateGoalWallet(goal: Goal, walletId: String) {
        db.goalDao().update(GoalEntity(goal.id, walletId, goal.targetAmount, goal.targetDate))
    }

    suspend fun updateGoalTargetAmount(goal: Goal, targetAmount: Double) {
        db.goalDao().update(GoalEntity(goal.id, goal.walletId, targetAmount, goal.targetDate))
    }

    suspend fun updateGoalTargetDate(goal: Goal, targetDate: String) {
        db.goalDao().update(GoalEntity(goal.id, goal.walletId, goal.targetAmount, targetDate))
    }

    // Mirrors js/core.js's convertCurrency(): cross-rate via UAH as the base,
    // falling back to SEED_RATES when no synced rate exists for a code yet.
    fun convertCurrency(amount: Double, from: String, to: String, rates: Map<String, Double>): Double {
        if (from == to) return amount
        val fromRate = rates[from] ?: SEED_RATES[from] ?: 1.0
        val toRate = rates[to] ?: SEED_RATES[to] ?: 1.0
        return Math.round(amount * fromRate / toRate * 100) / 100.0
    }

    companion object {
        // Mirrors js/analytics-csv.js's computeWalletBalances() — shared by
        // FinanceViewModel (hero balance/wallet chips) and
        // GoalsManagerViewModel (a goal's "saved" amount is its linked
        // wallet's own current balance, not a separate accumulator).
        fun walletBalance(transactions: List<Transaction>, walletId: String): Double {
            var balance = 0.0
            transactions.forEach { t ->
                when (t.type) {
                    TxType.INCOME -> if (t.walletId == walletId) balance += t.amount
                    TxType.EXPENSE -> if (t.walletId == walletId) balance -= t.amount
                    TxType.TRANSFER -> {
                        if (t.walletId == walletId) balance -= t.amount
                        if (t.targetWalletId == walletId) balance += (t.targetAmount ?: t.amount)
                    }
                }
            }
            return balance
        }
    }

    // Mirrors js/color-picker.js's computeNextDate(dateStr, freq).
    private fun computeNextDate(dateStr: String, frequency: String): String {
        val d = java.time.LocalDate.parse(dateStr)
        return when (frequency) {
            "daily" -> d.plusDays(1)
            "weekly" -> d.plusWeeks(1)
            else -> d.plusMonths(1) // monthly (default)
        }.toString()
    }

    // Mirrors js/color-picker.js's processRecurring(): materializes every active
    // recurring entry whose nextDate has fallen due (today or earlier) into a real
    // Transaction, advancing nextDate each time (guarded at 24 iterations/entry —
    // same guard as the PWA, protecting against a long-untouched nextDate spinning
    // through hundreds of daily occurrences in one go). Called once per cold-sync
    // sign-in (see MainActivity), same "runs on load, not continuously" scope as
    // the PWA's own call site inside fbLoadNow(). Local-only, same as every other
    // write in this app — Android has no continuous Firestore push yet (step 19's
    // disclosed scope), so newly materialized transactions stay local until the
    // remote catches up some other way.
    suspend fun processRecurring(): Int {
        val recurringList = db.recurringDao().getAllOnce()
        if (recurringList.isEmpty()) return 0
        val walletCurrency = db.walletDao().getAllOnce().associate { it.id to it.currency }
        val todayStr = java.time.LocalDate.now().toString()
        val newTx = mutableListOf<TransactionEntity>()
        val updated = mutableListOf<RecurringEntity>()
        var added = 0
        recurringList.forEach { r ->
            if (!r.active || r.amount <= 0) return@forEach
            var nextDate = r.nextDate
            var guard = 0
            while (nextDate <= todayStr && guard < 24) {
                newTx += TransactionEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    type = r.type,
                    amount = r.amount,
                    currency = walletCurrency[r.walletId] ?: "UAH",
                    date = nextDate,
                    walletId = r.walletId,
                    targetWalletId = null,
                    targetAmount = null,
                    targetCurrency = null,
                    category = r.category,
                    subcategory = null,
                    // "повторювана" — matches js/state.js's I18N.uk.recurring_comment_tag exactly.
                    comment = (if (r.comment.isNotBlank()) r.comment + " · " else "") + "повторювана",
                    tags = "",
                    createdAt = System.currentTimeMillis() + added,
                )
                nextDate = computeNextDate(nextDate, r.frequency)
                added++
                guard++
            }
            if (guard > 0) updated += r.copy(nextDate = nextDate)
        }
        if (added > 0) {
            db.transactionDao().insertAll(newTx)
            updated.forEach { db.recurringDao().update(it) }
        }
        return added
    }
}
