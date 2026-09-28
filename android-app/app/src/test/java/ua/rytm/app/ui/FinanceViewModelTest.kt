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

    private fun waitUntil(cond: () -> Boolean) { repeat(150) { if (cond()) return; Thread.sleep(20) } }

    private fun vm() = FinanceViewModel(repo, sync, auth, ActiveProfileStore(ApplicationProvider.getApplicationContext()))
        .also { v -> waitUntil { v.wallets.size == 2 } }

    private fun FinanceViewModel.addExpense(walletId: String, amount: String, category: String = "Food") {
        openNewTransactionSheet()
        onFormTypeChange(ua.rytm.app.ui.screens.finance.TxType.EXPENSE)
        onFormWalletChange(walletId)
        onFormAmountChange(amount)
        onFormCategoryChange(category)
        onFormDateChange(LocalDate.now().toString())
        submitForm()
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
        vm.onFormAmountChange("12.57coffee")
        assertEquals("12.57", vm.formAmountText)
    }

    @Test fun invalidAmountShowsValidationError() {
        val vm = vm()
        vm.addExpense("uah", "")
        assertEquals(R.string.validation_invalid_amount, vm.formErrorRes)
        assertTrue(runBlocking { db.transactionDao().getAllOnce() }.isEmpty())
    }

    @Test fun monthTotalsAreConvertedToUah() {
        runBlocking { db.currencyRateDao().insertAll(listOf(ua.rytm.app.data.local.CurrencyRateEntity("EUR", 50.0))) }
        val vm = vm()
        vm.addExpense("eur", "2")
        waitUntil { vm.monthExpenseUah == 100.0 }
        assertEquals(100.0, vm.monthExpenseUah, 0.001)
    }

    @Test fun failedSyncKeepsNothingAndReportsError() {
        coEvery { sync.saveTransaction(any(), any(), any()) } throws RuntimeException("offline")
        val vm = vm()
        vm.addExpense("uah", "5")
        waitUntil { vm.formErrorRes != null }
        assertEquals(R.string.transaction_save_failed, vm.formErrorRes)
        assertTrue(runBlocking { db.transactionDao().getAllOnce() }.isEmpty())
    }
}
