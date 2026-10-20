package ua.rytm.app.ui.screens.finance

import androidx.compose.runtime.derivedStateOf
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
import ua.rytm.app.data.BuiltinCategories
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
/** Balance(s) right after the form's transaction; [target] is set only for a transfer. */
data class FormBalanceAfter(val source: Double, val sourceCurrency: String, val target: Double?, val targetCurrency: String?)
/** One extra category of a split entry; the form's own category takes the rest. */
data class SplitPart(val category: String, val amountText: String)
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
    private val recurringSync: ua.rytm.app.data.RecurringSyncRepository? = null,
    private val settingsStore: ua.rytm.app.data.local.SettingsStore? = null,
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
                            lastMonthEarned = ua.rytm.app.ui.screens.shifts.EarningsForecast.forMonth(
                                java.time.YearMonth.now().minusMonths(1), LocalDate.now(), days, types, schedule,
                            ).earned,
                        )
                    },
                    app.recurringSyncRepository,
                    app.settingsStore,
                )
            }
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

    private fun markLoaded() { loading = !(walletsLoaded && transactionsLoaded); loadFailed = false }
    private fun markLoadFailed() { loading = false; loadFailed = true }

    // Declared before init: the flows below can emit synchronously while init
    // runs, and a property initialised later would reset what they wrote.
    private var shiftOutlook by mutableStateOf(ShiftOutlook())
    private val typicalShiftPay: Double? get() = shiftOutlook.typicalPay
    private var recurring by mutableStateOf<List<Recurring>>(emptyList())
    private var dismissedSubscriptions by mutableStateOf<Set<String>>(emptySet())
    private var monthSummarySeen by mutableStateOf<String?>(null)

    /** Today, as observable state so cached month totals roll over at midnight. */
    private var today by mutableStateOf(LocalDate.now())
    private val currentMonth: YearMonth get() = YearMonth.from(today)

    /** Last month's recap (MonthSummary). */
    val lastMonthSummary: MonthSummary by derivedStateOf {
        MonthSummary.compute(transactions, currentMonth.minusMonths(1)) { toUah(it.amount, it.currency) }
    }
    var monthSummaryOpen by mutableStateOf(false)

    /** The recap card shows in a month's first week until it is opened or closed. */
    val showMonthSummaryCard: Boolean
        get() = today.dayOfMonth <= 7 && !lastMonthSummary.isEmpty && monthSummarySeen != lastMonthSummary.month.toString()

    /** Everything the month's PDF report needs (MonthReport). */
    fun monthReport(month: YearMonth): MonthReport = MonthReport.build(transactions, month) { toUah(it.amount, it.currency) }

    fun walletDisplayName(id: String?): String = wallets.firstOrNull { it.id == id }?.name.orEmpty()

    fun reportExportFailed() { pendingMessage = FinanceMessage(R.string.common_save_failed) }

    fun openMonthSummary() { monthSummaryOpen = true; markMonthSummarySeen() }

    fun markMonthSummarySeen() {
        val month = lastMonthSummary.month.toString()
        monthSummarySeen = month
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch { runCatching { settingsStore?.setMonthSummarySeen(uid, month) } }
    }

    /** Repeating expenses that look like subscriptions, not yet recurring payments (SubscriptionDetector). */
    val subscriptionCandidates: List<SubscriptionDetector.Candidate> by derivedStateOf {
        SubscriptionDetector.detect(transactions, recurring, today, dismissedSubscriptions)
    }

    fun acceptSubscription(c: SubscriptionDetector.Candidate) {
        dismissedSubscriptions = dismissedSubscriptions + c.key // hide at once; the new recurring also covers it
        viewModelScope.launch {
            runCatching {
                repository.addRecurringFrom(c)
                val (ownerUid, profileId) = activeProfilePath()
                recurringSync?.saveRecurringSnapshot(ownerUid, profileId)
                pendingMessage = FinanceMessage(R.string.subscription_added, listOf(c.comment))
            }.onFailure { pendingMessage = FinanceMessage(R.string.common_save_failed) }
        }
    }

    fun dismissSubscription(c: SubscriptionDetector.Candidate) {
        dismissedSubscriptions = dismissedSubscriptions + c.key
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch { runCatching { settingsStore?.dismissSubscription(uid, c.key) } }
    }

    init {
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
        repository.goals.onEach { goals = it }.catch { }.launchIn(viewModelScope)
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                LocalDate.now().let { if (it != today) today = it }
            }
        }
        auth.currentUser?.uid?.let { uid ->
            settingsStore?.dismissedSubscriptions(uid)?.onEach { dismissedSubscriptions = it }?.catch { }?.launchIn(viewModelScope)
            settingsStore?.monthSummarySeen(uid)?.onEach { monthSummarySeen = it }?.catch { }?.launchIn(viewModelScope)
        }
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
    // A plain getter on purpose: recurring is a short list, and derivedStateOf
    // here went stale-forever when read off-composition in unit tests.
    val monthOutlook: MonthOutlook get() = run {
        FinanceInsights.monthOutlook(recurring, { id -> wallets.firstOrNull { it.id == id }?.currency }, today, shiftOutlook.remainingThisMonth, ::toUah)
    }

    /**
     * An expense priced in shifts of work ("≈ 1,4 зміни"), from the pay of
     * the shift type the user actually works most (EarningsForecast). Null
     * when there is no paid shift type or nothing typed yet.
     */
    private var goals by mutableStateOf<List<Goal>>(emptyList())

    /** A new income close to what last month's shifts earned: (expected, received − expected) in UAH. */
    val formSalaryMatch: Pair<Double, Double>?
        get() {
            if (form.type != TxType.INCOME || form.editingTxId != null) return null
            val amount = parseMoneyInput(form.amountText)?.takeIf { it > 0 } ?: return null
            val expected = shiftOutlook.lastMonthEarned
            val uah = toUah(amount, formWalletCurrency)
            return if (SalaryAutopilot.matchesExpectedPay(uah, expected)) expected to (uah - expected) else null
        }

    /** After a payday income is saved: suggested transfers to goal wallets (SalaryAllocationSheet). */
    var salaryAllocation by mutableStateOf<SalaryAllocation?>(null)

    fun applySalaryAllocation(shares: List<SalaryAutopilot.GoalShare>) {
        val plan = salaryAllocation ?: return
        salaryAllocation = null
        if (shares.isEmpty()) return
        val me = auth.currentUser
        viewModelScope.launch {
            runCatching {
                val (ownerUid, profileId) = activeProfilePath()
                shares.forEach { share ->
                    val tx = Transaction(
                        id = java.util.UUID.randomUUID().toString(), type = TxType.TRANSFER, amount = share.amount,
                        currency = plan.currency, date = LocalDate.now().toString(), walletId = plan.sourceWalletId,
                        targetWalletId = share.walletId, targetAmount = share.amount, targetCurrency = plan.currency,
                        category = BuiltinCategories.TRANSFER, comment = null,
                        createdBy = me?.uid, createdByName = me?.displayName?.takeIf { it.isNotBlank() } ?: me?.email?.substringBefore('@'),
                    )
                    syncRepository.saveTransaction(ownerUid, profileId, tx.toEntity())
                    repository.upsertTransaction(tx)
                }
                pendingMessage = FinanceMessage(R.string.salary_allocated, listOf(formatMoney(shares.sumOf { it.amount })))
            }.onFailure { pendingMessage = FinanceMessage(R.string.common_save_failed) }
        }
    }

    val formShiftCost: Double?
        get() {
            if (form.type != TxType.EXPENSE) return null
            val pay = typicalShiftPay?.takeIf { it > 0 } ?: return null
            val amount = parseMoneyInput(form.amountText)?.takeIf { it > 0 } ?: return null
            return toUah(amount, formWalletCurrency) / pay
        }

    /** Most used categories of the form's type over the last 90 days — one-tap picks above the dropdown. */
    val formFrequentCategories: List<String> by derivedStateOf { frequentCategoriesOf(form.type) }

    private fun frequentCategoriesOf(type: TxType): List<String> =
        FinanceInsights.frequentCategories(transactions, type, categoriesByType[type].orEmpty().toSet(), today.minusDays(90))

    /**
     * Shared profile, this month: income/expense per person who added the
     * entries. Shown only once at least two people have added something —
     * a solo profile never sees it.
     */
    val monthContributions: List<Contribution> by derivedStateOf {
        FinanceInsights.contributions(transactions, currentMonth, auth.currentUser?.uid, ::toUah)
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

    /** The new/edit sheet's draft (TransactionFormState). */
    val form = TransactionFormState(
        savedState,
        categoriesOf = { categoriesByType[it].orEmpty() },
        frequentOf = { frequentCategoriesOf(it) },
        autoRules = { autoRules },
        onMessage = { pendingMessage = it },
    )

    var isSaving by mutableStateOf(false)
        private set

    var pendingMessage by mutableStateOf<FinanceMessage?>(null)
        private set
    fun consumeMessage() { pendingMessage = null }

    /** One-line / voice entry dialog (QuickEntryDialog); it only pre-fills the form. */
    var quickEntryOpen by mutableStateOf(false)

    fun openNewTransactionSheet(prefill: ua.rytm.app.bank.TxPrefill? = null, type: TxType? = null) {
        val txType = type ?: when { prefill?.isTransfer == true -> TxType.TRANSFER; prefill?.isIncome == true -> TxType.INCOME; else -> TxType.EXPENSE }
        // A bank suggestion lands in a wallet of the same currency when there is one.
        // A wallet named with the card's last digits ("ПУМБ 5536") wins; then same currency; then the first.
        val walletId = (prefill?.walletId?.let { id -> wallets.firstOrNull { it.id == id } }
            ?: walletForCard(prefill?.account, prefill?.bank)
            ?: prefill?.let { p -> wallets.firstOrNull { it.currency == p.currency } } ?: wallets.firstOrNull())?.id.orEmpty()
        val targetId = (walletForCard(prefill?.targetAccount, prefill?.targetBank)?.takeIf { it.id != walletId }
            ?: wallets.firstOrNull { it.id != walletId } ?: wallets.getOrNull(1))?.id.orEmpty()
        val amount = prefill?.let { java.math.BigDecimal.valueOf(it.amount).stripTrailingZeros().toPlainString() }.orEmpty()
        form.openNew(txType, walletId, targetId, amount)
        // Through the normal setter so keyword auto-rules pick the category.
        prefill?.comment?.let { form.onCommentChange(it) }
    }

    private fun walletForCard(mask: String?, bankPackage: String? = null): Wallet? =
        ua.rytm.app.bank.WalletMatcher.forCard(wallets, mask, bankPackage)

    fun openEditTransactionSheet(tx: Transaction) = form.openEdit(tx, wallets.getOrNull(1)?.id.orEmpty())

    val formWalletCurrency: String
        get() = wallets.firstOrNull { it.id == form.walletId }?.currency ?: "UAH"

    val formSubcategoryOptions: List<String>
        get() = subcategoriesByKey[subKey(form.type.name, form.category.orEmpty())].orEmpty()

    val formTransferHint: TransferHint?
        get() {
            if (form.type != TxType.TRANSFER) return null
            if (form.walletId.isBlank() || form.targetWalletId.isBlank()) return null
            if (form.walletId == form.targetWalletId) return TransferHint(isWarning = true)
            val srcCur = formWalletCurrency
            val targetCur = wallets.firstOrNull { it.id == form.targetWalletId }?.currency ?: "UAH"
            // Same currency: "1,00 UAH -> 1,00 UAH" says nothing.
            if (srcCur == targetCur) return null
            val amount = parseMoneyInput(form.amountText)?.takeIf { it > 0 } ?: 1.0
            val converted = convertSample(amount, srcCur, targetCur)
            val sourceText = "${"%.2f".format(amount)} $srcCur"
            val targetText = "${"%.2f".format(converted)} $targetCur"
            return TransferHint(sourceText, targetText)
        }

    /**
     * The wallet balance(s) right after the transaction being edited/entered, live
     * from the form — like a bank's "balance after". An unparsable amount counts as 0;
     * a new entry sorts last on its date.
     */
    val formBalanceAfter: FormBalanceAfter? by derivedStateOf { computeFormBalanceAfter() }

    private fun computeFormBalanceAfter(): FormBalanceAfter? {
            if (form.walletId.isBlank()) return null
            val isTransfer = form.type == TxType.TRANSFER
            if (isTransfer && (form.targetWalletId.isBlank() || form.targetWalletId == form.walletId)) return null
            val amount = parseMoneyInput(form.amountText)?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
            val srcCur = formWalletCurrency
            val targetCur = if (isTransfer) wallets.firstOrNull { it.id == form.targetWalletId }?.currency ?: "UAH" else null
            val existing = form.editingTxId?.let { id -> transactions.firstOrNull { it.id == id } }
            val draft = Transaction(
                id = existing?.id ?: "__draft__", type = form.type, amount = amount, currency = srcCur,
                date = form.date, walletId = form.walletId, category = "",
                targetWalletId = if (isTransfer) form.targetWalletId else null,
                targetAmount = targetCur?.let { convertSample(amount, srcCur, it) }, targetCurrency = targetCur,
                createdAt = existing?.createdAt ?: Long.MAX_VALUE,
            )
            return FormBalanceAfter(
                source = FinanceRepository.balanceAfter(transactions, form.walletId, draft),
                sourceCurrency = srcCur,
                target = targetCur?.let { FinanceRepository.balanceAfter(transactions, form.targetWalletId, draft) },
                targetCurrency = targetCur,
            )
    }

    fun submitForm() {
        if (isSaving) return
        val amount = parseMoneyInput(form.amountText) ?: Double.NaN
        val isTransfer = form.type == TxType.TRANSFER
        val draft = TransactionDraft(
            amount = amount,
            date = form.date,
            walletId = form.walletId,
            targetWalletId = if (isTransfer) form.targetWalletId else null,
            category = if (isTransfer) BuiltinCategories.TRANSFER else form.category,
            subcategory = if (isTransfer) null else form.subcategory,
            comment = form.comment.trim(),
        )
        val error = validateTransactionDraft(draft, isTransfer)
        if (error != null) { form.showError(error.field, when (error) {
            TxValidationError.INVALID_AMOUNT -> R.string.validation_invalid_amount
            TxValidationError.AMOUNT_TOO_LARGE -> R.string.validation_amount_too_large
            TxValidationError.DATE_REQUIRED -> R.string.validation_date_required
            TxValidationError.INVALID_DATE -> R.string.validation_invalid_date
            TxValidationError.WALLET_REQUIRED -> R.string.validation_wallet_required
            TxValidationError.COMMENT_TOO_LONG -> R.string.validation_comment_too_long
            TxValidationError.CATEGORY_TOO_LONG -> R.string.validation_category_too_long
            TxValidationError.SAME_WALLETS -> R.string.validation_same_wallets
        }); return }

        val splitParts = if (form.canSplit) form.splits else emptyList()
        val splitAmounts = splitParts.map { parseMoneyInput(it.amountText) ?: Double.NaN }
        if (splitParts.isNotEmpty() && (splitAmounts.any { !(it > 0) } || !(amount - splitAmounts.sum() > 0))) {
            form.showError(TxFormField.AMOUNT, R.string.split_invalid)
            return
        }

        val srcCur = formWalletCurrency
        var targetAmount: Double? = null
        var targetCurrency: String? = null
        if (isTransfer) {
            targetCurrency = wallets.firstOrNull { it.id == form.targetWalletId }?.currency ?: "UAH"
            targetAmount = convertSample(amount, srcCur, targetCurrency)
        }

        val editingId = form.editingTxId
        val existing = editingId?.let { id -> transactions.firstOrNull { it.id == id } }
        val me = auth.currentUser
        val toSave = (existing ?: Transaction(
            id = java.util.UUID.randomUUID().toString(), type = form.type, amount = amount, date = form.date, walletId = form.walletId, category = draft.category ?: BuiltinCategories.OTHER,
            createdBy = me?.uid,
            createdByName = me?.displayName?.takeIf { it.isNotBlank() } ?: me?.email?.substringBefore('@'),
        )).copy(
            type = form.type, amount = amount - splitAmounts.sum(), currency = srcCur,
            category = draft.category ?: BuiltinCategories.OTHER, subcategory = draft.subcategory,
            walletId = form.walletId,
            targetWalletId = if (isTransfer) form.targetWalletId else null,
            targetAmount = targetAmount, targetCurrency = targetCurrency,
            date = form.date, comment = draft.comment.ifBlank { null },
            tags = form.selectedTagIds,
        )
        isSaving = true
        viewModelScope.launch {
            runCatching {
                val (ownerUid, profileId) = activeProfilePath()
                syncRepository.saveTransaction(ownerUid, profileId, toSave.toEntity())
                repository.upsertTransaction(toSave)
                splitParts.forEachIndexed { i, part ->
                    val extra = toSave.copy(
                        id = java.util.UUID.randomUUID().toString(), amount = splitAmounts[i],
                        category = part.category, subcategory = null, createdAt = toSave.createdAt + i + 1,
                    )
                    syncRepository.saveTransaction(ownerUid, profileId, extra.toEntity())
                    repository.upsertTransaction(extra)
                }
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
                    splitParts.isNotEmpty() -> FinanceMessage(R.string.split_saved, listOf(splitParts.size + 1))
                    existing != null -> FinanceMessage(R.string.transaction_updated)
                    isTransfer -> FinanceMessage(R.string.transaction_transfer_done)
                    else -> FinanceMessage(R.string.transaction_added)
                }
                if (existing == null && toSave.type == TxType.INCOME &&
                    SalaryAutopilot.matchesExpectedPay(toUah(toSave.amount, toSave.currency), shiftOutlook.lastMonthEarned)
                ) {
                    val shares = SalaryAutopilot.suggest(
                        toSave.amount, toSave.walletId, toSave.currency, goals,
                        walletCurrency = { id -> wallets.firstOrNull { it.id == id }?.currency },
                        walletBalance = { id -> FinanceRepository.walletBalance(transactions + toSave, id) },
                        today = LocalDate.now(),
                    )
                    if (shares.isNotEmpty()) salaryAllocation = SalaryAllocation(toSave.walletId, toSave.currency, shares, toSave.comment)
                }
                form.finishSaved()
            }.onFailure {
                form.showError(null, R.string.transaction_save_failed)
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
    val totalBalanceUah: Double by derivedStateOf { wallets.sumOf { w -> toUah(walletBalance(w.id), w.currency) } }

    /** Daily allowance until month end (FinanceInsights.safeToSpendPerDay); null = bills exceed the balance. */
    val safeToSpendPerDay: Double?
        get() = FinanceInsights.safeToSpendPerDay(totalBalanceUah, monthOutlook.recurringOut, today)

    val isMultiCurrency: Boolean by derivedStateOf { wallets.map { it.currency }.distinct().size > 1 }

    private val currentMonthPrefix: String get() = currentMonth.toString() // "2026-08"

    val monthIncomeUah: Double by derivedStateOf { FinanceInsights.monthTotalUah(transactions, TxType.INCOME, currentMonth, ::toUah) }

    val monthExpenseUah: Double by derivedStateOf { FinanceInsights.monthTotalUah(transactions, TxType.EXPENSE, currentMonth, ::toUah) }

    val isSearchOrFilterActive: Boolean
        get() = search.isNotBlank() || typeFilter != TxTypeFilter.ALL || categoryFilter != null

    /** Mirrors renderFinance()'s filter chain: type -> period -> category -> search -> sort newest-first.
     *  Cached: it used to be a plain getter, re-filtering and re-sorting every row on each read. */
    val filteredTransactions: List<Transaction> by derivedStateOf { computeFilteredTransactions() }

    /** Source-wallet balance right after each transaction (the history rows' optional "balance after"). */
    val balancesAfter: Map<String, Double> by derivedStateOf { FinanceRepository.runningBalances(transactions) }

    /** Net in UAH per date (transfers excluded) — the day headers' sums, one pass instead of one filter per day. */
    fun dayNetsUah(txs: List<Transaction>): Map<String, Double> =
        txs.groupBy { it.date }.mapValues { (_, day) -> netUah(day) }

    private fun computeFilteredTransactions(): List<Transaction> =
        FinanceInsights.filter(
            transactions, typeFilter, periodFilter, categoryFilter, search, today,
            walletName = { id -> wallets.firstOrNull { it.id == id }?.name?.lowercase() ?: "" },
            tagNames = tags.associate { it.id to it.name },
        )
}

/** Payday suggestion: move these amounts from [sourceWalletId] to goal wallets. */
data class SalaryAllocation(val sourceWalletId: String, val currency: String, val shares: List<SalaryAutopilot.GoalShare>, val comment: String?)

data class ShiftOutlook(val typicalPay: Double? = null, val remainingThisMonth: Double = 0.0, val lastMonthEarned: Double = 0.0)

data class MonthOutlook(val shiftsEarnings: Double, val recurringOut: Double, val recurringIn: Double) {
    val isEmpty: Boolean get() = shiftsEarnings <= 0 && recurringOut <= 0 && recurringIn <= 0
}

data class Contribution(val name: String, val isMe: Boolean, val income: Double, val expense: Double)

/** "A pays B" so the month's expenses end up split equally between members. */
data class Settlement(val from: String, val to: String, val amount: Double)

/**
 * Equal split of everyone's expenses: whoever paid less than the average owes
 * the difference to whoever paid more. Greedy largest-debtor → largest-creditor,
 * at most n-1 payments; amounts under 1 ₴ are rounding noise and dropped.
 */
fun settleEqually(contributions: List<Contribution>): List<Settlement> {
    if (contributions.size < 2) return emptyList()
    val share = contributions.sumOf { it.expense } / contributions.size
    val debtors = contributions.map { it.name to share - it.expense }.filter { it.second >= 1 }.sortedByDescending { it.second }.toMutableList()
    val creditors = contributions.map { it.name to it.expense - share }.filter { it.second >= 1 }.sortedByDescending { it.second }.toMutableList()
    val out = mutableListOf<Settlement>()
    while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
        val (d, owe) = debtors.first()
        val (c, due) = creditors.first()
        val pay = minOf(owe, due)
        if (pay >= 1) out += Settlement(d, c, Math.round(pay * 100) / 100.0)
        if (owe - pay < 1) debtors.removeAt(0) else debtors[0] = d to owe - pay
        if (due - pay < 1) creditors.removeAt(0) else creditors[0] = c to due - pay
    }
    return out
}
