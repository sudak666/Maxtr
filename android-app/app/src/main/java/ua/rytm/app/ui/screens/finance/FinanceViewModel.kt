package ua.rytm.app.ui.screens.finance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.TransactionsSyncRepository
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
    private val auth: FirebaseAuth,
    private val activeProfileStore: ActiveProfileStore,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    shiftOutlookFlow: kotlinx.coroutines.flow.Flow<ShiftOutlook> = kotlinx.coroutines.flow.flowOf(ShiftOutlook()),
) : ViewModel() {

    companion object {
        fun factory(app: RytmApplication) = viewModelFactory {
            initializer {
                FinanceViewModel(
                    app.financeRepository,
                    app.transactionsSyncRepository,
                    FirebaseAuth.getInstance(),
                    app.activeProfileStore,
                    createSavedStateHandle(),
                    kotlinx.coroutines.flow.combine(
                        app.shiftsRepository.shiftTypes,
                        app.shiftsRepository.shiftsByDate,
                        app.shiftsRepository.autoFillSchedule,
                    ) { types, days, schedule ->
                        val month = ua.rytm.app.ui.screens.shifts.EarningsForecast.forMonth(
                            java.time.YearMonth.now(), LocalDate.now(), days, types, schedule,
                        )
                        ShiftOutlook(
                            typicalPay = ua.rytm.app.ui.screens.shifts.EarningsForecast.typicalShiftPay(days, types, schedule),
                            remainingThisMonth = month.planned + month.fromSchedule,
                        )
                    },
                )
            }
        }

        private const val DRAFT_KEY = "tx_draft"
    }

    /**
     * The half-typed transaction survives process death, not just rotation.
     *
     * A ViewModel without a SavedStateHandle dies with its process, and
     * Android OEMs kill backgrounded processes aggressively — losing a
     * half-entered expense is exactly the kind of thing Play Console tracks
     * as a quality signal. Only the draft is persisted (all Strings), never
     * the loaded data, which is re-read from Room anyway.
     */
    private fun persistDraft() {
        savedState[DRAFT_KEY] = arrayListOf(
            if (sheetVisible) "1" else "0",
            editingTxId.orEmpty(),
            formType.name,
            formWalletId,
            formTargetWalletId,
            formAmountText,
            formCategory.orEmpty(),
            formSubcategory.orEmpty(),
            formDate,
            formComment,
            formSelectedTagIds.joinToString("\u001f"),
        )
    }

    private fun restoreDraft() {
        val saved: ArrayList<String> = savedState[DRAFT_KEY] ?: return
        if (saved.size < 11) return
        sheetVisible = saved[0] == "1"
        editingTxId = saved[1].takeIf { it.isNotEmpty() }
        formType = runCatching { TxType.valueOf(saved[2]) }.getOrDefault(TxType.EXPENSE)
        formWalletId = saved[3]
        formTargetWalletId = saved[4]
        formAmountText = saved[5]
        formCategory = saved[6].takeIf { it.isNotEmpty() }
        formSubcategory = saved[7].takeIf { it.isNotEmpty() }
        formDate = saved[8]
        formComment = saved[9]
        formSelectedTagIds = saved[10].split("\u001f").filter { it.isNotEmpty() }
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

    private fun markLoaded() { loading = !(walletsLoaded && transactionsLoaded); loadFailed = false }
    private fun markLoadFailed() { loading = false; loadFailed = true }

    // Declared before init: the flows below can emit synchronously while init
    // runs, and a property initialised later would reset what they wrote.
    private var shiftOutlook by mutableStateOf(ShiftOutlook())
    private val typicalShiftPay: Double? get() = shiftOutlook.typicalPay
    private var recurring by mutableStateOf<List<Recurring>>(emptyList())

    init {
        restoreDraft()
        viewModelScope.launch { runCatching { repository.seedIfEmpty() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; markLoadFailed() } }
        repository.wallets.onEach { wallets = it; walletsLoaded = true; markLoaded() }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.transactions.onEach { transactions = it; transactionsLoaded = true; markLoaded() }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.categoriesByType.onEach { categoriesByType = it }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.subcategoriesByKey.onEach { subcategoriesByKey = it }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.tags.onEach { tags = it }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.categoryIcons.onEach { categoryIcons = it }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.autoRules.onEach { autoRules = it }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.currencyRates.onEach { currencyRates = it }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.budgets.onEach { budgets = it }.catch { markLoadFailed() }.launchIn(viewModelScope)
        shiftOutlookFlow.onEach { shiftOutlook = it }.catch { }.launchIn(viewModelScope)
        repository.recurring.onEach { recurring = it }.catch { }.launchIn(viewModelScope)
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

    fun deleteTransaction(id: String, animationDelayMs: Long = 0L, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                if (animationDelayMs > 0) delay(animationDelayMs)
                val (ownerUid, profileId) = activeProfilePath()
                syncRepository.deleteTransaction(ownerUid, profileId, id)
                repository.deleteTransaction(id)
            }.onSuccess {
                onComplete(true)
            }.onFailure {
                pendingMessage = FinanceMessage(R.string.transaction_delete_failed)
                onComplete(false)
            }
        }
    }

    fun restoreTransaction(transaction: Transaction, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val (ownerUid, profileId) = activeProfilePath()
                val entity = transaction.toEntity()
                syncRepository.saveTransaction(ownerUid, profileId, entity)
                repository.upsertTransaction(transaction)
            }.onSuccess {
                onComplete(true)
            }.onFailure {
                pendingMessage = FinanceMessage(R.string.transaction_save_failed)
                onComplete(false)
            }
        }
    }


    /**
     * What is still coming before the month ends — the link between the two
     * halves of this app no tracker makes: shifts placed (or scheduled by
     * autofill) after today, and active recurring payments due by month end.
     * Shown as two separate facts, not folded into a projected balance: shift
     * pay often lands next month, so a single number would mislead.
     */
    val monthOutlook: MonthOutlook
        get() {
            val today = LocalDate.now()
            val end = java.time.YearMonth.now().atEndOfMonth()
            var outgoing = 0.0
            var incoming = 0.0
            recurring.filter { it.active && it.amount > 0 }.forEach { r ->
                val currency = wallets.firstOrNull { it.id == r.walletId }?.currency ?: "UAH"
                var date = runCatching { LocalDate.parse(r.nextDate) }.getOrNull() ?: return@forEach
                var guard = 0
                while (!date.isAfter(end) && guard < 62) {
                    if (!date.isBefore(today)) {
                        val uah = toUah(r.amount, currency)
                        if (r.type == TxType.EXPENSE) outgoing += uah else incoming += uah
                    }
                    date = when (r.frequency) { "daily" -> date.plusDays(1); "weekly" -> date.plusWeeks(1); else -> date.plusMonths(1) }
                    guard++
                }
            }
            return MonthOutlook(shiftsEarnings = shiftOutlook.remainingThisMonth, recurringOut = outgoing, recurringIn = incoming)
        }

    /**
     * An expense priced in shifts of work ("≈ 1,4 зміни"), from the pay of
     * the shift type the user actually works most (EarningsForecast). Null
     * when there is no paid shift type or nothing typed yet.
     */
    val formShiftCost: Double?
        get() {
            if (formType != TxType.EXPENSE) return null
            val pay = typicalShiftPay?.takeIf { it > 0 } ?: return null
            val amount = parseMoneyInput(formAmountText)?.takeIf { it > 0 } ?: return null
            return toUah(amount, formWalletCurrency) / pay
        }

    /** Most used categories of the form's type over the last 90 days — one-tap picks above the dropdown. */
    val formFrequentCategories: List<String>
        get() {
            if (formType == TxType.TRANSFER) return emptyList()
            val known = categoriesByType[formType].orEmpty().toSet()
            val since = LocalDate.now().minusDays(90).toString()
            return transactions.asSequence()
                .filter { it.type == formType && it.date >= since && it.category in known }
                .groupingBy { it.category }.eachCount()
                .entries.sortedByDescending { it.value }.take(6).map { it.key }
        }

    /**
     * Shared profile, this month: income/expense per person who added the
     * entries. Shown only once at least two people have added something —
     * a solo profile never sees it.
     */
    val monthContributions: List<Contribution>
        get() {
            val mine = auth.currentUser?.uid
            val rows = transactions.filter { it.date.startsWith(currentMonthPrefix) && it.createdBy != null && it.type != TxType.TRANSFER }
            val byPerson = rows.groupBy { it.createdBy!! }
            if (byPerson.size < 2) return emptyList()
            return byPerson.map { (uid, txs) ->
                Contribution(
                    name = txs.firstNotNullOfOrNull { it.createdByName } ?: "?",
                    isMe = uid == mine,
                    income = txs.filter { it.type == TxType.INCOME }.sumOf { toUah(it.amount, it.currency) },
                    expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { toUah(it.amount, it.currency) },
                )
            }.sortedByDescending { it.isMe }
        }

    /** Income minus expense in UAH (transfers excluded) — the per-day header total. */
    fun netUah(txs: List<Transaction>): Double = txs.sumOf {
        when (it.type) {
            TxType.INCOME -> toUah(it.amount, it.currency)
            TxType.EXPENSE -> -toUah(it.amount, it.currency)
            TxType.TRANSFER -> 0.0
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
    /** Which field [formErrorRes] belongs to, so the form can mark it. */
    var formErrorField by mutableStateOf<TxFormField?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set

    fun toggleFormTag(id: String) {
        formSelectedTagIds = if (id in formSelectedTagIds) formSelectedTagIds - id else formSelectedTagIds + id
        persistDraft()
    }

    var pendingMessage by mutableStateOf<FinanceMessage?>(null)
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
        formErrorRes = null
        formErrorField = null
        sheetVisible = true
        persistDraft()
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
        formErrorField = null
        sheetVisible = true
        persistDraft()
    }

    fun closeSheet() { sheetVisible = false; persistDraft() }

    fun onFormTypeChange(type: TxType) {
        formType = type
        formCategory = categoriesByType[type]?.firstOrNull()
        formSubcategory = null
        persistDraft()
    }

    fun onFormWalletChange(id: String) { formWalletId = id; persistDraft() }
    fun onFormTargetWalletChange(id: String) { formTargetWalletId = id; persistDraft() }
    fun onFormAmountChange(text: String) {
        // Pasted/hardware-keyboard text could put letters into the amount field.
        formAmountText = text.filter { it.isDigit() || it == '.' || it == ',' || it == ' ' || it == ' ' }
        formErrorRes = null
        formErrorField = null
        persistDraft()
    }
    fun onFormCategoryChange(category: String) {
        formCategory = category
        formSubcategory = null
        persistDraft()
    }
    fun onFormSubcategoryChange(sub: String?) { formSubcategory = sub; persistDraft() }
    fun onFormDateChange(date: String) { formDate = date; persistDraft() }
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
        persistDraft()
    }
    fun setFormDateToday() { formDate = LocalDate.now().toString(); persistDraft() }
    fun setFormAmount(value: Double) {
        formAmountText = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        formErrorRes = null
        formErrorField = null
        persistDraft()
    }

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
            val amount = parseMoneyInput(formAmountText)?.takeIf { it > 0 } ?: 1.0
            val converted = convertSample(amount, srcCur, targetCur)
            val sourceText = "${"%.2f".format(amount)} $srcCur"
            val targetText = "${"%.2f".format(converted)} $targetCur"
            return TransferHint(sourceText, targetText)
        }

    fun submitForm() {
        if (isSaving) return
        val amount = parseMoneyInput(formAmountText) ?: Double.NaN
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
        if (error != null) { formErrorField = error.field; formErrorRes = when (error) {
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
        val me = auth.currentUser
        val toSave = (existing ?: Transaction(
            id = java.util.UUID.randomUUID().toString(), type = formType, amount = amount, date = formDate, walletId = formWalletId, category = draft.category ?: "Інше",
            createdBy = me?.uid,
            createdByName = me?.displayName?.takeIf { it.isNotBlank() } ?: me?.email?.substringBefore('@'),
        )).copy(
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
                syncRepository.saveTransaction(ownerUid, profileId, toSave.toEntity())
                repository.upsertTransaction(toSave)
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
                    FinanceMessage(R.string.transaction_budget_exceeded, listOf(it.category, formatMoney(it.spent), formatMoney(it.limit)))
                } ?: when {
                    existing != null -> FinanceMessage(R.string.transaction_updated)
                    isTransfer -> FinanceMessage(R.string.transaction_transfer_done)
                    else -> FinanceMessage(R.string.transaction_added)
                }
                sheetVisible = false
            }.onFailure {
                formErrorField = null
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
                val tagNames = tags.associate { it.id to it.name }
                // "#кава" finds the tag "кава"; a bare word matches tag names too.
                val tagQuery = q.removePrefix("#")
                result = result.filter { t ->
                    listOfNotNull(t.comment, t.category, t.subcategory, walletName(t.walletId), walletName(t.targetWalletId), t.currency, t.targetCurrency)
                        .any { it.lowercase().contains(q) } ||
                        (tagQuery.isNotEmpty() && t.tags.any { tagNames[it]?.lowercase()?.contains(tagQuery) == true })
                }
            }

            return result.sortedByDescending { it.date }
        }
}

data class ShiftOutlook(val typicalPay: Double? = null, val remainingThisMonth: Double = 0.0)

data class MonthOutlook(val shiftsEarnings: Double, val recurringOut: Double, val recurringIn: Double) {
    val isEmpty: Boolean get() = shiftsEarnings <= 0 && recurringOut <= 0 && recurringIn <= 0
}

data class Contribution(val name: String, val isMe: Boolean, val income: Double, val expense: Double)
