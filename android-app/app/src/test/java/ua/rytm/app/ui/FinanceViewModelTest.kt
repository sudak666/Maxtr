package ua.rytm.app.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.rytm.app.R
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.TransactionsSyncRepository
import ua.rytm.app.data.local.ActiveProfileStore
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.ui.screens.finance.FinanceViewModel
import ua.rytm.app.ui.screens.finance.Wallet
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FinanceViewModelTest {
    private lateinit var db: RytmDatabase
    private lateinit var repo: FinanceRepository
    private val sync = mockk<TransactionsSyncRepository>(relaxed = true)
    private val auth = mockk<FirebaseAuth>().also { a ->
        val user = mockk<FirebaseUser>()
        every { user.uid } returns "u"
        every { user.displayName } returns "Me"
        every { user.email } returns "me@example.com"
        every { a.currentUser } returns user
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RytmDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FinanceRepository(db)
        runBlocking {
            repo.addWallet(Wallet("uah", "Cash", 0xFF00FF00, "UAH"))
            repo.addWallet(Wallet("eur", "Euro", 0xFF0000FF, "EUR"))
        }
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    // filteredTransactions & co. are derivedStateOf: their cache refreshes when
    // the global snapshot advances, which in the app every frame does. Unit
    // tests have no frames, so advance it here (it used to read a stale empty
    // list now and then — CI flake, #583..#591).
    private fun waitUntil(cond: () -> Boolean) {
        repeat(1500) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            if (cond()) return
            Thread.sleep(20)
        }
        // Fail here, not on a later .single() with a bare NoSuchElementException.
        throw AssertionError("waitUntil timed out after 30s")
    }

    private fun vm() = FinanceViewModel(repo, sync, auth, ActiveProfileStore(ApplicationProvider.getApplicationContext()))
        .also { v -> waitUntil { v.wallets.size == 2 } }

    private fun FinanceViewModel.addExpense(walletId: String, amount: String, category: String = "Food") {
        // submitForm() ignores a second save while the previous one is still
        // finishing (isSaving) — Room can emit the row before that flag drops.
        waitUntil { !isSaving }
        openNewTransactionSheet()
        form.onTypeChange(ua.rytm.app.ui.screens.finance.TxType.EXPENSE)
        form.onWalletChange(walletId)
        form.onAmountChange(amount)
        form.onCategoryChange(category)
        form.onDateChange(LocalDate.now().toString())
        submitForm()
    }

    // Recurring due between today and month end counts every occurrence
    // (weekly → several), past-dated and inactive ones don't; shift pay comes
    // from the outlook flow untouched.
    @Test fun monthOutlookSumsRemainingRecurringAndShifts() {
        val today = LocalDate.now()
        runBlocking {
            repo.replaceRecurring(listOf(
                ua.rytm.app.data.local.RecurringEntity("w", "EXPENSE", 100.0, "Rent", "uah", "weekly", today.toString(), true, ""),
                ua.rytm.app.data.local.RecurringEntity("off", "EXPENSE", 999.0, "X", "uah", "monthly", today.toString(), false, ""),
                ua.rytm.app.data.local.RecurringEntity("in", "INCOME", 50.0, "Gift", "uah", "monthly", today.toString(), true, ""),
            ))
        }
        val v = FinanceViewModel(
            repo, sync, auth, ActiveProfileStore(ApplicationProvider.getApplicationContext()),
            shiftOutlookFlow = kotlinx.coroutines.flow.flowOf(ua.rytm.app.ui.screens.finance.ShiftOutlook(1000.0, 3000.0)),
        )
        waitUntil { v.wallets.size == 2 && v.monthOutlook.recurringOut > 0 }
        val end = java.time.YearMonth.now().atEndOfMonth()
        val weeklyHits = generateSequence(today) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.count()
        assertEquals(100.0 * weeklyHits, v.monthOutlook.recurringOut, 0.001)
        assertEquals(50.0, v.monthOutlook.recurringIn, 0.001)
        assertEquals(3000.0, v.monthOutlook.shiftsEarnings, 0.001)
    }

    // New entries carry their author; contributions appear only with 2+ people.
    @Test fun contributionsNeedTwoPeopleAndNewEntriesCarryAuthor() {
        val v = vm()
        v.addExpense("uah", "100")
        waitUntil { v.filteredTransactions.size == 1 }
        assertEquals("u", v.filteredTransactions.single().createdBy)
        assertEquals("Me", v.filteredTransactions.single().createdByName)
        assertTrue(v.monthContributions.isEmpty())
        runBlocking {
            repo.upsertTransaction(ua.rytm.app.ui.screens.finance.Transaction(
                id = "p", type = ua.rytm.app.ui.screens.finance.TxType.EXPENSE, amount = 40.0, date = LocalDate.now().toString(),
                walletId = "uah", category = "Food", createdBy = "partner", createdByName = "Оля",
            ))
        }
        waitUntil { v.monthContributions.size == 2 }
        val c = v.monthContributions
        assertTrue(c.first().isMe)
        assertEquals(100.0, c.first().expense, 0.001)
        assertEquals("Оля", c[1].name)
        assertEquals(40.0, c[1].expense, 0.001)
    }

    @Test fun submitPersistsAndSyncs() {
        val vm = vm()
        vm.addExpense("uah", "12,5")
        waitUntil { runBlocking { db.transactionDao().getAllOnce() }.isNotEmpty() && vm.pendingMessage != null }
        val tx = runBlocking { db.transactionDao().getAllOnce() }.single()
        assertEquals(12.5, tx.amount, 0.0)
        assertEquals("UAH", tx.currency)
        coVerify { sync.saveTransaction("u", "default", any()) }
        assertEquals(R.string.transaction_added, vm.pendingMessage?.resource)
    }

    @Test fun amountFieldDropsLetters() {
        val vm = vm()
        vm.form.onAmountChange("12.57coffee")
        assertEquals("12.57", vm.form.amountText)
    }

    @Test fun invalidAmountShowsValidationError() {
        val vm = vm()
        vm.addExpense("uah", "")
        assertEquals(R.string.validation_invalid_amount, vm.form.errorRes)
        assertTrue(runBlocking { db.transactionDao().getAllOnce() }.isEmpty())
    }

    @Test fun monthTotalsAreConvertedToUah() {
        runBlocking { db.currencyRateDao().insertAll(listOf(ua.rytm.app.data.local.CurrencyRateEntity("EUR", 50.0))) }
        val vm = vm()
        vm.addExpense("eur", "2")
        waitUntil { vm.monthExpenseUah == 100.0 }
        assertEquals(100.0, vm.monthExpenseUah, 0.001)
    }

    @Test fun searchMatchesTagNames() {
        runBlocking { db.tagDao().insert(ua.rytm.app.data.local.TagEntity("t1", "Коло", 0xFFFF0000)) }
        val vm = vm()
        waitUntil { vm.tags.isNotEmpty() }
        vm.openNewTransactionSheet()
        vm.form.onTypeChange(ua.rytm.app.ui.screens.finance.TxType.EXPENSE)
        vm.form.onWalletChange("uah")
        vm.form.onAmountChange("5")
        vm.form.onCategoryChange("Food")
        vm.form.onDateChange(LocalDate.now().toString())
        vm.form.toggleTag("t1")
        vm.submitForm()
        waitUntil { vm.filteredTransactions.size == 1 }
        vm.addExpense("uah", "7", category = "Fun")
        waitUntil { vm.filteredTransactions.size == 2 }
        vm.onSearchChange("коло")
        assertEquals(listOf(5.0), vm.filteredTransactions.map { it.amount })
        vm.onSearchChange("#КОЛ")
        assertEquals(1, vm.filteredTransactions.size)
        vm.onSearchChange("#")
        assertEquals(0, vm.filteredTransactions.size)
    }

    @Test fun failedSyncKeepsNothingAndReportsError() {
        coEvery { sync.saveTransaction(any(), any(), any()) } throws RuntimeException("offline")
        val vm = vm()
        vm.addExpense("uah", "5")
        waitUntil { vm.form.errorRes != null }
        assertEquals(R.string.transaction_save_failed, vm.form.errorRes)
        assertTrue(runBlocking { db.transactionDao().getAllOnce() }.isEmpty())
    }

    // One receipt, two categories: the part becomes its own entry, the form's category keeps the rest.
    @Test fun splitSavesOneEntryPerPart() {
        val v = vm()
        v.openNewTransactionSheet()
        v.form.onTypeChange(ua.rytm.app.ui.screens.finance.TxType.EXPENSE)
        v.form.onWalletChange("uah")
        v.form.onAmountChange("1000")
        v.form.onCategoryChange("Food")
        v.form.onCommentChange("АТБ")
        v.form.addSplitPart()
        v.form.updateSplitPart(0, ua.rytm.app.ui.screens.finance.SplitPart("Home", "300"))
        v.submitForm()
        waitUntil { v.filteredTransactions.size == 2 }
        val byCat = v.filteredTransactions.associateBy { it.category }
        assertEquals(700.0, byCat.getValue("Food").amount, 0.001)
        assertEquals(300.0, byCat.getValue("Home").amount, 0.001)
        assertTrue(v.filteredTransactions.all { it.comment == "АТБ" && it.walletId == "uah" })
        assertTrue(v.form.splits.isEmpty())
    }

    // Parts that eat the whole total are rejected and nothing is saved.
    @Test fun splitRejectsPartsNotBelowTotal() {
        val v = vm()
        v.openNewTransactionSheet()
        v.form.onTypeChange(ua.rytm.app.ui.screens.finance.TxType.EXPENSE)
        v.form.onWalletChange("uah")
        v.form.onAmountChange("100")
        v.form.onCategoryChange("Food")
        v.form.addSplitPart()
        v.form.updateSplitPart(0, ua.rytm.app.ui.screens.finance.SplitPart("Home", "100"))
        v.submitForm()
        assertEquals(R.string.split_invalid, v.form.errorRes)
        assertTrue(v.form.sheetVisible)
    }

}
