package ua.rytm.app.ui.screens.finance

import androidx.annotation.StringRes
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import ua.rytm.app.R
import ua.rytm.app.data.local.AutoRuleEntity
import java.time.LocalDate

/**
 * The new/edit transaction sheet's draft: every field the user types, its
 * validation error, the split parts, and their survival across process death
 * (SavedStateHandle). Split out of FinanceViewModel, which keeps what needs the
 * loaded data or the repositories (wallet resolution, balance-after, saving).
 *
 * Mirrors setFinanceType()/readTransactionForm() in js/finance.js
 * (FINANCE_SCREEN_SPEC.md §9).
 */
class TransactionFormState(
    private val savedState: SavedStateHandle,
    /** The type's category list. */
    private val categoriesOf: (TxType) -> List<String>,
    /** The type's most used categories, first = the default pick. */
    private val frequentOf: (TxType) -> List<String>,
    private val autoRules: () -> List<AutoRuleEntity>,
    private val onMessage: (FinanceMessage) -> Unit,
) {
    var sheetVisible by mutableStateOf(false)
        private set
    var editingTxId by mutableStateOf<String?>(null)
        private set
    var type by mutableStateOf(TxType.EXPENSE)
        private set
    var walletId by mutableStateOf("")
        private set
    var targetWalletId by mutableStateOf("")
        private set
    var amountText by mutableStateOf("")
        private set
    var category by mutableStateOf<String?>(null)
        private set
    var subcategory by mutableStateOf<String?>(null)
        private set
    var date by mutableStateOf(LocalDate.now().toString())
        private set
    var comment by mutableStateOf("")
        private set
    var selectedTagIds by mutableStateOf<List<String>>(emptyList())
        private set
    @get:StringRes
    var errorRes by mutableStateOf<Int?>(null)
        private set
    /** Which field [errorRes] belongs to, so the form can mark it; null = a form-level error. */
    var errorField by mutableStateOf<TxFormField?>(null)
        private set
    /** Extra categories of a split entry (new income/expense only). Empty = an ordinary entry. */
    var splits by mutableStateOf<List<SplitPart>>(emptyList())
        private set

    val canSplit: Boolean get() = editingTxId == null && type != TxType.TRANSFER

    /** What stays in the form's own category: total minus the parts; null without a split. */
    val splitRemainder: Double? by derivedStateOf {
        if (splits.isEmpty()) null
        else (parseMoneyInput(amountText) ?: 0.0) - splits.sumOf { parseMoneyInput(it.amountText) ?: 0.0 }
    }

    init { restore() }

    fun openNew(type: TxType, walletId: String, targetWalletId: String, amountText: String) {
        editingTxId = null
        this.type = type
        this.walletId = walletId
        this.targetWalletId = targetWalletId
        this.amountText = amountText
        // The type's most-used category, not the alphabetically first one.
        category = frequentOf(type).firstOrNull() ?: categoriesOf(type).firstOrNull()
        subcategory = null
        date = LocalDate.now().toString()
        comment = ""
        selectedTagIds = emptyList()
        splits = emptyList()
        clearError()
        sheetVisible = true
        persist()
    }

    fun openEdit(tx: Transaction, fallbackTargetWalletId: String) {
        editingTxId = tx.id
        type = tx.type
        walletId = tx.walletId
        targetWalletId = tx.targetWalletId ?: fallbackTargetWalletId
        amountText = plainNumber(tx.amount)
        category = tx.category
        subcategory = tx.subcategory
        date = tx.date
        comment = tx.comment.orEmpty()
        selectedTagIds = tx.tags
        splits = emptyList()
        clearError()
        sheetVisible = true
        persist()
    }

    fun close() { sheetVisible = false; persist() }

    /** After a successful save. */
    fun finishSaved() { splits = emptyList(); sheetVisible = false; persist() }

    fun onTypeChange(type: TxType) {
        this.type = type
        category = frequentOf(type).firstOrNull() ?: categoriesOf(type).firstOrNull()
        subcategory = null
        // Parts were picked from the other type's categories.
        splits = emptyList()
        persist()
    }

    fun onWalletChange(id: String) { walletId = id; persist() }
    fun onTargetWalletChange(id: String) { targetWalletId = id; persist() }
    fun onAmountChange(text: String) {
        // Pasted/hardware-keyboard text could put letters into the amount field.
        amountText = text.filter { it.isDigit() || it == '.' || it == ',' || it == ' ' || it == ' ' }
        clearError()
        persist()
    }
    fun onCategoryChange(category: String) {
        this.category = category
        subcategory = null
        persist()
    }
    fun onSubcategoryChange(sub: String?) { subcategory = sub; persist() }
    fun onDateChange(date: String) { this.date = date; persist() }
    fun onCommentChange(text: String) {
        if (text.length <= TX_COMMENT_MAX) {
            comment = text
            if (type != TxType.TRANSFER) {
                val rule = autoRules().firstOrNull { it.type.equals(type.name, true) && it.keyword.isNotEmpty() && text.lowercase().contains(it.keyword.lowercase()) }
                if (rule != null && rule.category in categoriesOf(type) && category != rule.category) {
                    category = rule.category
                    subcategory = null
                    onMessage(FinanceMessage(R.string.transaction_auto_category, listOf(rule.category)))
                }
            }
        }
        persist()
    }
    fun setDateToday() { date = LocalDate.now().toString(); persist() }
    fun setAmount(value: Double) {
        amountText = plainNumber(value)
        clearError()
        persist()
    }
    fun toggleTag(id: String) {
        selectedTagIds = if (id in selectedTagIds) selectedTagIds - id else selectedTagIds + id
        persist()
    }

    fun addSplitPart() {
        val cats = categoriesOf(type)
        val used = splits.map { it.category }.toSet() + listOfNotNull(category)
        splits = splits + SplitPart(cats.firstOrNull { it !in used } ?: cats.firstOrNull().orEmpty(), "")
        clearError()
        persist()
    }
    fun updateSplitPart(index: Int, part: SplitPart) {
        splits = splits.toMutableList().also { if (index in it.indices) it[index] = part.copy(amountText = part.amountText.filter { c -> c.isDigit() || c == '.' || c == ',' || c == ' ' }) }
        clearError()
        persist()
    }
    fun removeSplitPart(index: Int) {
        splits = splits.filterIndexed { i, _ -> i != index }
        persist()
    }

    fun showError(field: TxFormField?, @StringRes res: Int) { errorField = field; errorRes = res }
    fun clearError() { errorRes = null; errorField = null }

    private fun plainNumber(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /**
     * The half-typed transaction survives process death, not just rotation.
     *
     * A ViewModel without a SavedStateHandle dies with its process, and
     * Android OEMs kill backgrounded processes aggressively — losing a
     * half-entered expense is exactly the kind of thing Play Console tracks
     * as a quality signal. Only the draft is persisted (all Strings), never
     * the loaded data, which is re-read from Room anyway.
     */
    private fun persist() {
        savedState[DRAFT_KEY] = arrayListOf(
            if (sheetVisible) "1" else "0",
            editingTxId.orEmpty(),
            type.name,
            walletId,
            targetWalletId,
            amountText,
            category.orEmpty(),
            subcategory.orEmpty(),
            date,
            comment,
            selectedTagIds.joinToString("\u001f"),
            splits.joinToString("\u001d") { it.category + "\u001e" + it.amountText },
        )
    }

    private fun restore() {
        val saved: ArrayList<String> = savedState[DRAFT_KEY] ?: return
        if (saved.size < 11) return
        sheetVisible = saved[0] == "1"
        editingTxId = saved[1].takeIf { it.isNotEmpty() }
        type = runCatching { TxType.valueOf(saved[2]) }.getOrDefault(TxType.EXPENSE)
        walletId = saved[3]
        targetWalletId = saved[4]
        amountText = saved[5]
        category = saved[6].takeIf { it.isNotEmpty() }
        subcategory = saved[7].takeIf { it.isNotEmpty() }
        date = saved[8]
        comment = saved[9]
        selectedTagIds = saved[10].split("\u001f").filter { it.isNotEmpty() }
        splits = saved.getOrNull(11).orEmpty().split("\u001d").filter { it.isNotEmpty() }
            .map { it.split("\u001e").let { p -> SplitPart(p[0], p.getOrElse(1) { "" }) } }
    }

    private companion object { const val DRAFT_KEY = "tx_draft" }
}
