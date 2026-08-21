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
import kotlinx.coroutines.launch
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.subKey
import java.time.LocalDate
import java.time.YearMonth

// Backed by Room via FinanceRepository (ANDROID_MIGRATION.md §2,
// FINANCE_SCREEN_SPEC.md §8) — data is real and persisted, though still
// bootstrapped from SampleFinanceData on first launch since there's no
// Firestore/auth sync yet (see FinanceRepository.seedIfEmpty()'s comment).
// Filtering logic mirrors renderFinance() in js/analytics-csv.js
// line-for-line (see FINANCE_SCREEN_SPEC.md §5) so behavior parity is
// checkable against the real PWA, not guessed.
class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository) = viewModelFactory {
            initializer { FinanceViewModel(repository) }
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
    private var transactions by mutableStateOf<List<Transaction>>(emptyList())

    // Sample-only approximate USD->UAH rate. Real rates come from
    // AppState.currencyRates (Firestore) once real sync exists.
    private val sampleRatesToUah = mapOf("UAH" to 1.0, "USD" to 41.5)

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
        repository.wallets.onEach { wallets = it }.launchIn(viewModelScope)
        repository.transactions.onEach { transactions = it }.launchIn(viewModelScope)
        repository.categoriesByType.onEach { categoriesByType = it }.launchIn(viewModelScope)
        repository.subcategoriesByKey.onEach { subcategoriesByKey = it }.launchIn(viewModelScope)
        repository.tags.onEach { tags = it }.launchIn(viewModelScope)
        repository.categoryIcons.onEach { categoryIcons = it }.launchIn(viewModelScope)
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

    fun onSearchChange(value: String) { search = value }
    fun clearSearch() { search = "" }
    fun onTypeFilterChange(value: TxTypeFilter) { typeFilter = value }
    fun onPeriodFilterChange(value: PeriodFilter) { periodFilter = value }
    fun clearCategoryFilter() { categoryFilter = null }
    fun toggleListExpanded() { listExpanded = !listExpanded }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }

    private fun toUah(amount: Double, currency: String): Double =
        amount * (sampleRatesToUah[currency] ?: 1.0)

    /** Cross-rate via UAH as the base, matching convertCurrency() in js/core.js. */
    private fun convertSample(amount: Double, from: String, to: String): Double {
        if (from == to) return amount
        val uah = toUah(amount, from)
        val toRate = sampleRatesToUah[to] ?: 1.0
        return uah / toRate
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
    var formError by mutableStateOf<String?>(null)
        private set

    fun toggleFormTag(id: String) {
        formSelectedTagIds = if (id in formSelectedTagIds) formSelectedTagIds - id else formSelectedTagIds + id
    }

    var pendingMessage by mutableStateOf<String?>(null)
        private set
    fun consumeMessage() { pendingMessage = null }

    fun openNewTransactionSheet() {
        editingTxId = null
        formType = TxType.EXPENSE
        formWalletId = wallets.firstOrNull()?.id.orEmpty()
        formTargetWalletId = wallets.getOrNull(1)?.id.orEmpty()
        formAmountText = ""
        formCategory = categoriesByType[TxType.EXPENSE]?.firstOrNull()
        formSubcategory = null
        formDate = LocalDate.now().toString()
        formComment = ""
        formSelectedTagIds = emptyList()
        formError = null
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
        formError = null
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
        if (text.length <= TX_COMMENT_MAX) formComment = text
    }
    fun setFormDateToday() { formDate = LocalDate.now().toString() }
    fun setFormAmount(value: Double) { formAmountText = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString() }

    val formWalletCurrency: String
        get() = wallets.firstOrNull { it.id == formWalletId }?.currency ?: "UAH"

    val formSubcategoryOptions: List<String>
        get() = subcategoriesByKey[subKey(formType.name, formCategory.orEmpty())].orEmpty()

    val formTransferHint: Pair<String, Boolean>? // text, isWarning
        get() {
            if (formType != TxType.TRANSFER) return null
            if (formWalletId.isBlank() || formTargetWalletId.isBlank()) return null
            if (formWalletId == formTargetWalletId) return "Оберіть інший гаманець для переказу." to true
            val srcCur = formWalletCurrency
            val targetCur = wallets.firstOrNull { it.id == formTargetWalletId }?.currency ?: "UAH"
            val amount = formAmountText.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
            val converted = convertSample(amount, srcCur, targetCur)
            val sourceText = "${"%.2f".format(amount)} $srcCur"
            val targetText = "${"%.2f".format(converted)} $targetCur"
            return "Орієнтовно за поточним курсом: $sourceText → $targetText" to false
        }

    fun submitForm() {
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
        if (error != null) { formError = error; return }

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
        viewModelScope.launch { repository.upsertTransaction(toSave) }
        pendingMessage = when {
            existing != null -> "Запис оновлено"
            isTransfer -> "Переказ виконано"
            else -> "Запис додано"
        }
        sheetVisible = false
    }

    /** Per-wallet balance in the wallet's own currency — mirrors computeWalletBalances(). */
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
