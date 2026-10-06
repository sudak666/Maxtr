package ua.rytm.app.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.rytm.app.R
import ua.rytm.app.data.DebtRepository
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.ui.screens.debt.DebtSaver
import ua.rytm.app.ui.screens.debt.DebtViewModel

private class FakeDebtSaver(var target: DebtSaver.Target = DebtSaver.Target.Editable("u", "default"), val saveDelayMs: Long = 0) : DebtSaver {
    var saves = 0
    var fail = false
    override suspend fun target() = target
    override suspend fun save(ownerUid: String, profileId: String, currentDebtId: Long?) {
        kotlinx.coroutines.withContext(Dispatchers.IO) { Thread.sleep(saveDelayMs) }; saves++
        if (fail) throw RuntimeException("offline")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DebtViewModelTest {
    private lateinit var db: RytmDatabase
    private lateinit var repo: DebtRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RytmDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DebtRepository(db)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    private fun waitUntil(cond: () -> Boolean) { repeat(150) { if (cond()) return; Thread.sleep(20) } }

    private fun newVm(saver: FakeDebtSaver): DebtViewModel {
        val vm = DebtViewModel(repo, saver, fieldSaveDebounceMs = 0)
        vm.addDebt("Loan", "Debt")
        waitUntil { vm.currentDebt != null }
        return vm
    }

    // Regression (2026-09-28, live): each keystroke committed, and edits made
    // while a save was in flight were dropped — "1000,5" was saved as 1.
    @Test fun fastKeystrokeEditsAllLand() {
        val saver = FakeDebtSaver(saveDelayMs = 100)
        val vm = newVm(saver)
        val typed = listOf("1", "10", "100", "1000", "1000,", "1000,5")
        typed.forEach { vm.updateInfo("Loan", "", "UAH", ua.rytm.app.ui.screens.finance.parseMoneyInput(it) ?: 0.0, "") }
        waitUntil { vm.currentDebt?.startAmount == 1000.5 && !vm.saving }
        assertEquals(1000.5, vm.currentDebt!!.startAmount, 0.0)
    }

    @Test fun paymentWithDecimalCommaUpdatesBalance() {
        val vm = newVm(FakeDebtSaver())
        vm.updateInfo("Loan", "", "UAH", 1000.0, "")
        waitUntil { vm.currentDebt?.startAmount == 1000.0 }
        vm.addEntry("200,5", "", "")
        waitUntil { vm.currentDebt?.entries?.isNotEmpty() == true }
        val entry = vm.currentDebt!!.entries.single()
        assertEquals("200.5", entry.amount)
        assertEquals(799.5, entry.balance, 0.001)
    }

    @Test fun readOnlyProfileRefusesEdits() {
        val saver = FakeDebtSaver()
        val vm = newVm(saver)
        saver.target = DebtSaver.Target.ReadOnly
        vm.updateInfo("Renamed", "", "UAH", 5.0, "")
        waitUntil { vm.errorMessageRes != null }
        assertEquals(R.string.profile_read_only, vm.errorMessageRes)
        assertEquals("Loan", vm.currentDebt!!.name)
    }

    @Test fun failedSaveRollsBack() {
        val saver = FakeDebtSaver()
        val vm = newVm(saver)
        saver.fail = true
        vm.updateInfo("Renamed", "", "UAH", 5.0, "")
        waitUntil { vm.errorMessageRes != null }
        assertEquals(R.string.common_save_failed, vm.errorMessageRes)
        waitUntil { vm.currentDebt?.name == "Loan" }
        assertEquals("Loan", vm.currentDebt?.name)
    }

    // Field edits go through the same save path (debounce is 0 in tests).
    @Test fun failedFieldSaveReportsError() {
        val saver = FakeDebtSaver()
        val vm = newVm(saver)
        saver.fail = true
        vm.updateInfo("Renamed", "", "UAH", 5.0, "")
        waitUntil { vm.errorMessageRes != null }
        assertEquals(R.string.common_save_failed, vm.errorMessageRes)
    }
}
