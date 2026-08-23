package ua.rytm.app.ui.screens.finance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.FinanceSnapshotOutboxRepository
import ua.rytm.app.data.TransactionsSyncRepository
import ua.rytm.app.data.TransactionSyncState
import ua.rytm.app.data.local.ActiveProfileStore
import ua.rytm.app.data.toEntity
import com.google.firebase.auth.FirebaseAuth
import ua.rytm.app.data.subKey
import ua.rytm.app.data.local.AutoRuleEntity
import java.time.LocalDate
import java.time.YearMonth
import androidx.annotation.StringRes
import ua.rytm.app.R

data class TransferHint(val sourceText: String = "", val targetText: String = "", val isWarning: Boolean = false)
data class FinanceMessage(@StringRes val resource: Int, val arguments: List<Any> = emptyList())

// Backed by Room via FinanceRepository (ANDROID_MIGRATION.md §2,
// FINANCE_SCREEN_SPEC.md §8) — data is real and persisted, though still
// bootstrapped from the PWA's exact empty-profile defaults.
// Filtering logic mirrors renderFinance() in js/analytics-csv.js
// line-for-line (see FINANCE_SCREEN_SPEC.md §5) so behavior parity is
// checkable against the real PWA, not guessed.
class FinanceViewModel(
    private val repository: FinanceRepository,
    private val syncRepository: TransactionsSyncRepository,
    private val snapshotOutbox: FinanceSnapshotOutboxRepository,
    private val auth: FirebaseAuth,
    private val activeProfileStore: ActiveProfileStore,
) : ViewModel() {

    companion object {
        fun factory(app: RytmApplication) = viewModelFactory {
            initializer { FinanceViewModel(app.financeRepository, app.transactionsSyncRepository, app.financeSnapshotOutboxRepository, FirebaseAuth.getInstance(), app.activeProfileStore) }
        }
    }

    var wallets by mutableStateOf<List<Wallet>>(emptyList())
        private set
    var categoriesByType by mutableStateOf<Map<TxType, List<String>>>(emptyMap())
        private set
    private var subcategoriesByKey by mutableStateOf<Map<String, List<String>>>(emptyMap())
    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set
    var categoryIcons by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var autoRules by mutableStateOf<List<AutoRuleEntity>>(emptyList())
    private var transactions by mutableStateOf<List<Transaction>>(emptyList())
    private var currencyRates by mutableStateOf<Map<String, Double>>(emptyMap())
    private var budgets by mutableStateOf<Map<String, Double>>(emptyMap())
    private var walletsLoaded = false
    private var transactionsLoaded = false
    var loading by mutableStateOf(true)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var transactionSyncStates by mutableStateOf<Map<String, TransactionSyncState>>(emptyMap())
        private set
    var financeSnapshotSyncState by mutableStateOf<TransactionSyncState?>(null)
        private set

    private fun markLoaded() { loading = !(walletsLoaded && transactionsLoaded); loadFailed = false }
    private fun markLoadFailed() { loading = false; loadFailed = true }

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
        repository.wallets.onEach { wallets = it; walletsLoaded = true; markLoaded() }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.transactions.onEach { transactions = it; transactionsLoaded = true; markLoaded() }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.categoriesByType.onEach { categoriesByType = it }.launchIn(viewModelScope)
        repository.subcategoriesByKey.onEach { subcategoriesByKey = it }.launchIn(viewModelScope)
        repository.tags.onEach { tags = it }.launchIn(viewModelScope)
        repository.categoryIcons.onEach { categoryIcons = it }.launchIn(viewModelScope)
        repository.autoRules.onEach { autoRules = it }.launchIn(viewModelScope)
        repository.currencyRates.onEach { currencyRates = it }.launchIn(viewModelScope)
        repository.budgets.onEach { budgets = it }.launchIn(viewModelScope)
        syncRepository.operationStates.onEach { transactionSyncStates = it }.launchIn(viewModelScope)
        snapshotOutbox.operationState.onEach { financeSnapshotSyncState = it }.launchIn(viewModelScope)
    }

    var search by mutableStateOf("")
        private set
    var typeFilter by mutableStateOf(TxTypeFilter.ALL)
        private set
    var periodFilter by mutableStateOf(PeriodFilter.ALL)
        private set
    var categoryFilter by mutableStateOf<String?>(null)
        private set
    var listExpanded by mutableStateOf(false)
        private set
    var selectedTransactionIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var pendingUndoTransactions by mutableStateOf<List<Transaction>>(emptyList())
        private set

    fun onSearchChange(value: String) { search = value }
    fun clearSearch() { search = "" }
    fun onTypeFilterChange(value: TxTypeFilter) { typeFilter = value }
    fun onPeriodFilterChange(value: PeriodFilter) { periodFilter = value }
    fun clearCategoryFilter() { categoryFilter = null }
    fun toggleListExpanded() { listExpanded = !listExpanded }
    fun toggleTransactionSelection(id: String) {
        selectedTransactionIds = if (id in selectedTransactionIds) selectedTransactionIds - id else selectedTransactionIds + id
    }
    fun clearTransactionSelection() { selectedTransactionIds = emptySet() }

    fun deleteTransaction(id: String) {
        deleteTransactions(setOf(id))
    }

    fun deleteSelectedTransactions() = deleteTransactions(selectedTransactionIds)

    private fun deleteTransactions(ids: Set<String>) {
        if (ids.isEmpty()) return
        val removed = transactions.filter { it.id in ids }
        viewModelScope.launch {
            runCatching {
                val (ownerUid, profileId) = activeProfilePath()
                syncRepository.queueDeletes(ownerUid, profileId, ids)
            }.onSuccess {
                selectedTransactionIds = emptySet()
                pendingUndoTransactions = removed
            }.onFailure { pendingMessage = FinanceMessage(R.string.transaction_delete_failed) }
        }
    }

    fun consumeUndoTransactions() { pendingUndoTransactions = emptyList() }

    fun undoTransactionDelete() {
        val restored = pendingUndoTransactions
        if (restored.isEmpty()) return
        pendingUndoTransactions = emptyList()
        viewModelScope.launch {
            runCatching {
                val entities = restored.map { it.toEntity() }
                val (ownerUid, profileId) = activeProfilePath()
                syncRepository.queueSaves(ownerUid, profileId, entities)
            }.onFailure { pendingMessage = FinanceMessage(R.string.transaction_restore_failed) }
        }
    }

    fun updateSelectedCategory(category: String) {
        val updated = transactions.filter { it.id in selectedTransactionIds }.map { it.copy(category = category, subcategory = null) }
        if (updated.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val entities = updated.map { it.toEntity() }
                val (ownerUid, profileId) = activeProfilePath()
                syncRepository.queueSaves(ownerUid, profileId, entities)
            }.onSuccess { selectedTransactionIds = emptySet() }
                .onFailure { pendingMessage = FinanceMessage(R.string.transaction_save_failed) }
        }
    }

    private fun toUah(amount: Double, currency: String): Double =
        repository.convertCurrency(amount, currency, "UAH", currencyRates)

    /** Cross-rate via UAH as the base, matching convertCurrency() in js/core.js. */
    private fun convertSample(amount: Double, from: String, to: String): Double {
        if (from == to) return amount
        return repository.convertCurrency(amount, from, to, currencyRates)
    }

    // ---- New/edit transaction sheet — mirrors setFinanceType()/
    // readTransactionForm()/addTransaction()/editTransaction() in
    // js/finance.js (FINANCE_SCREEN_SPEC.md §9). ----

    var sheetVisible by mutableStateOf(false)
        private set
    var editingTxId by mutableStateOf<String?>(null)
        private set

    var formType by mutableStateOf(TxType.EXPENSE)
        private set
    var formWalletId by mutableStateOf("")
        private set
    var formTargetWalletId by mutableStateOf("")
        private set
    var formAmountText by mutableStateOf("")
        private set
    var formCategory by mutableStateOf<String?>(null)
        private set
    var formSubcategory by mutableStateOf<String?>(null)
        private set
    var formDate by mutableStateOf(LocalDate.now().toString())
        private set
    var formComment by mutableStateOf("")
        private set
    var formSelectedTagIds by mutableStateOf<List<String>>(emptyList())
        private set
    @get:StringRes
    var formErrorRes by mutableStateOf<Int?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set

    fun toggleFormTag(id: String) {
        formSelectedTagIds = if (id in formSelectedTagIds) formSelectedTagIds - id else formSelectedTagIds + id
    }

    var pendingMessage by mutableStateOf<FinanceMessage?>(null)
        private set
    fun consumeMessage() { pendingMessage = null }

    fun openNewTransactionSheet() = openNewTransactionSheet(null)

    fun openNewTransactionSheet(sharedText: String?) {
        editingTxId = null
        formType = TxType.EXPENSE
        formWalletId = wallets.firstOrNull()?.id.orEmpty()
        formTargetWalletId = wallets.getOrNull(1)?.id.orEmpty()
        formAmountText = ""
        formCategory = categoriesByType[TxType.EXPENSE]?.firstOrNull()
        formSubcategory = null
        formDate = LocalDate.now().toString()
        formComment = sharedText.orEmpty().take(500)
        formSelectedTagIds = emptyList()
        formErrorRes = null
        sheetVisible = true
    }

    fun openEditTransactionSheet(tx: Transaction) {
        editingTxId = tx.id
        formType = tx.type
        formWalletId = tx.walletId
        formTargetWalletId = tx.targetWalletId ?: wallets.getOrNull(1)?.id.orEmpty()
        formAmountText = if (tx.amount == tx.amount.toLong().toDouble()) tx.amount.toLong().toString() else tx.amount.toString()
        formCategory = tx.category
        formSubcategory = tx.subcategory
        formDate = tx.date
        formComment = tx.comment.orEmpty()
        formSelectedTagIds = tx.tags
        formErrorRes = null
        sheetVisible = true
    }

    fun closeSheet() { sheetVisible = false }

    fun onFormTypeChange(type: TxType) {
        formType = type
        formCategory = categoriesByType[type]?.firstOrNull()
        formSubcategory = null
    }

    fun onFormWalletChange(id: String) { formWalletId = id }
    fun onFormTargetWalletChange(id: String) { formTargetWalletId = id }
    fun onFormAmountChange(text: String) { formAmountText = text }
    fun onFormCategoryChange(category: String) {
        formCategory = category
        formSubcategory = null
    }
    fun onFormSubcategoryChange(sub: String?) { formSubcategory = sub }
    fun onFormDateChange(date: String) { formDate = date }
    fun onFormCommentChange(text: String) {
        if (text.length <= TX_COMMENT_MAX) {
            formComment = text
            if (formType != TxType.TRANSFER) {
                val rule = autoRules.firstOrNull { it.type.equals(formType.name, true) && it.keyword.isNotEmpty() && text.lowercase().contains(it.keyword.lowercase()) }
                if (rule != null && rule.category in categoriesByType[formType].orEmpty() && formCategory != rule.category) {
                    formCategory = rule.category
                    formSubcategory = null
                    pendingMessage = FinanceMessage(R.string.transaction_auto_category, listOf(rule.category))
                }
            }
        }
    }
    fun setFormDateToday() { formDate = LocalDate.now().toString() }
    fun setFormAmount(value: Double) { formAmountText = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString() }

    val formWalletCurrency: String
        get() = wallets.firstOrNull { it.id == formWalletId }?.currency ?: "UAH"

    val formSubcategoryOptions: List<String>
        get() = subcategoriesByKey[subKey(formType.name, formCategory.orEmpty())].orEmpty()

    val formTransferHint: TransferHint?
        get() {
            if (formType != TxType.TRANSFER) return null
            if (formWalletId.isBlank() || formTargetWalletId.isBlank()) return null
            if (formWalletId == formTargetWalletId) return TransferHint(isWarning = true)
            val srcCur = formWalletCurrency
            val targetCur = wallets.firstOrNull { it.id == formTargetWalletId }?.currency ?: "UAH"
            val amount = formAmountText.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
            val converted = convertSample(amount, srcCur, targetCur)
            val sourceText = formatMoneyWithCurrency(amount, srcCur)
            val targetText = formatMoneyWithCurrency(converted, targetCur)
            return TransferHint(sourceText, targetText)
        }

    fun submitForm() {
        if (isSaving) return
        val amount = formAmountText.toDoubleOrNull() ?: Double.NaN
        val isTransfer = formType == TxType.TRANSFER
        val draft = TransactionDraft(
            amount = amount,
            date = formDate,
            walletId = formWalletId,
            targetWalletId = if (isTransfer) formTargetWalletId else null,
            category = if (isTransfer) "Внутрішній переказ" else formCategory,
            subcategory = if (isTransfer) null else formSubcategory,
            comment = formComment.trim(),
        )
        val error = validateTransactionDraft(draft, isTransfer)
        if (error != null) { formErrorRes = when (error) {
            TxValidationError.INVALID_AMOUNT -> R.string.validation_invalid_amount
            TxValidationError.AMOUNT_TOO_LARGE -> R.string.validation_amount_too_large
            TxValidationError.DATE_REQUIRED -> R.string.validation_date_required
            TxValidationError.INVALID_DATE -> R.string.validation_invalid_date
            TxValidationError.WALLET_REQUIRED -> R.string.validation_wallet_required
            TxValidationError.COMMENT_TOO_LONG -> R.string.validation_comment_too_long
            TxValidationError.CATEGORY_TOO_LONG -> R.string.validation_category_too_long
            TxValidationError.SAME_WALLETS -> R.string.validation_same_wallets
        }; return }

        val srcCur = formWalletCurrency
        var targetAmount: Double? = null
        var targetCurrency: String? = null
        if (isTransfer) {
            targetCurrency = wallets.firstOrNull { it.id == formTargetWalletId }?.currency ?: "UAH"
            targetAmount = convertSample(amount, srcCur, targetCurrency)
        }

        val editingId = editingTxId
        val existing = editingId?.let { id -> transactions.firstOrNull { it.id == id } }
        val toSave = (existing ?: Transaction(id = java.util.UUID.randomUUID().toString(), type = formType, amount = amount, date = formDate, walletId = formWalletId, category = draft.category ?: "Інше")).copy(
            type = formType, amount = amount, currency = srcCur,
            category = draft.category ?: "Інше", subcategory = draft.subcategory,
            walletId = formWalletId,
            targetWalletId = if (isTransfer) formTargetWalletId else null,
            targetAmount = targetAmount, targetCurrency = targetCurrency,
            date = formDate, comment = draft.comment.ifBlank { null },
            tags = formSelectedTagIds,
        )
        isSaving = true
        viewModelScope.launch {
            runCatching {
                val (ownerUid, profileId) = activeProfilePath()
                syncRepository.queueSave(ownerUid, profileId, toSave.toEntity())
            }.onSuccess {
                val budgetFeedback = if (toSave.type == TxType.EXPENSE) {
                    budgetExceededFeedback(
                        category = toSave.category,
                        limit = budgets[toSave.category],
                        existingMonthAmountsUah = transactions.asSequence()
                            .filter { it.id != toSave.id && it.type == TxType.EXPENSE && it.category == toSave.category && it.date.startsWith(currentMonthPrefix) }
                            .map { toUah(it.amount, it.currency) }
                            .toList(),
                        savedAmountUah = toUah(toSave.amount, toSave.currency),
                    )
                } else null
                pendingMessage = budgetFeedback?.let {
                    FinanceMessage(R.string.transaction_budget_exceeded, listOf(it.category, formatMoneyWithCurrency(it.spent, "UAH"), formatMoneyWithCurrency(it.limit, "UAH")))
                } ?: when {
                    existing != null -> FinanceMessage(R.string.transaction_updated)
                    isTransfer -> FinanceMessage(R.string.transaction_transfer_done)
                    else -> FinanceMessage(R.string.transaction_added)
                }
                sheetVisible = false
            }.onFailure {
                formErrorRes = R.string.transaction_save_failed
            }
            isSaving = false
        }
    }

    private suspend fun activeProfilePath(): Pair<String, String> {
        val accountUid = checkNotNull(auth.currentUser?.uid) { "Потрібна авторизація" }
        val profileId = activeProfileStore.getActiveProfileId(accountUid)
        val ownerUid = activeProfileStore.getActiveProfileOwnerUid(accountUid) ?: accountUid
        return ownerUid to profileId
    }

    /** Per-wallet balance in the wallet's own currency - mirrors computeWalletBalances(). */
    fun walletBalance(walletId: String): Double = FinanceRepository.walletBalance(transactions, walletId)

    /** Total balance across all wallets, converted to UAH — mirrors renderFinance()'s `bal`. */
    val totalBalanceUah: Double
        get() = wallets.sumOf { w -> toUah(walletBalance(w.id), w.currency) }

    val isMultiCurrency: Boolean
        get() = wallets.map { it.currency }.distinct().size > 1

    private val currentMonthPrefix: String
        get() = YearMonth.now().toString() // "2026-08"

    val monthIncomeUah: Double
        get() = transactions
            .filter { it.type == TxType.INCOME && it.date.startsWith(currentMonthPrefix) }
            .sumOf { toUah(it.amount, it.currency) }

    val monthExpenseUah: Double
        get() = transactions
            .filter { it.type == TxType.EXPENSE && it.date.startsWith(currentMonthPrefix) }
            .sumOf { toUah(it.amount, it.currency) }

    val isSearchOrFilterActive: Boolean
        get() = search.isNotBlank() || typeFilter != TxTypeFilter.ALL || categoryFilter != null

    /** Mirrors renderFinance()'s filter chain: type -> period -> category -> search -> sort newest-first. */
    val filteredTransactions: List<Transaction>
        get() {
            var result: List<Transaction> = transactions

            result = when (typeFilter) {
                TxTypeFilter.ALL -> result
                TxTypeFilter.INCOME -> result.filter { it.type == TxType.INCOME }
                TxTypeFilter.EXPENSE -> result.filter { it.type == TxType.EXPENSE }
                TxTypeFilter.TRANSFER -> result.filter { it.type == TxType.TRANSFER }
            }

            result = when (periodFilter) {
                PeriodFilter.DAY -> {
                    val today = LocalDate.now().toString()
                    result.filter { it.date == today }
                }
                PeriodFilter.MONTH -> result.filter { it.date.startsWith(currentMonthPrefix) }
                PeriodFilter.ALL -> result
            }

            categoryFilter?.let { cat -> result = result.filter { it.category == cat } }

            if (search.isNotBlank()) {
                val q = search.trim().lowercase()
                fun walletName(id: String?) = wallets.firstOrNull { it.id == id }?.name?.lowercase() ?: ""
                result = result.filter { t ->
                    listOfNotNull(t.comment, t.category, t.subcategory, walletName(t.walletId), walletName(t.targetWalletId), t.currency, t.targetCurrency)
                        .any { it.lowercase().contains(q) }
                }
            }

            return result.sortedByDescending { it.date }
        }
}
