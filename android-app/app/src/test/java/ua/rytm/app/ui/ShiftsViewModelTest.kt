package ua.rytm.app.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
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
import ua.rytm.app.data.ShiftsRepository
import ua.rytm.app.data.ShiftsSyncRepository
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.ui.screens.shifts.ShiftsViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ShiftsViewModelTest {
    private lateinit var db: RytmDatabase
    private lateinit var repo: ShiftsRepository
    private val sync = mockk<ShiftsSyncRepository>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RytmDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ShiftsRepository(db, sync)
        runBlocking { repo.seedFreshProfileDefaults() }
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    private fun waitUntil(cond: () -> Boolean) { repeat(100) { if (cond()) return; Thread.sleep(20) } }

    @Test fun savingADaySelectionPersistsAndSyncs() {
        val vm = ShiftsViewModel(repo, "u", "default")
        waitUntil { vm.shiftTypes.isNotEmpty() }
        val type = vm.shiftTypes.first()
        vm.openDayModal("2026-09-01")
        vm.toggleDayModalType(type.id)
        vm.saveDayModal()
        waitUntil { vm.shiftsFor("2026-09-01").isNotEmpty() }
        assertEquals(listOf(type.id), vm.shiftsFor("2026-09-01").map { it.id })
        coVerify { sync.saveShiftDays("u", "default") }
    }

    @Test fun failedSyncRollsBackTheDay() {
        coEvery { sync.saveShiftDays(any(), any()) } throws RuntimeException("offline")
        val vm = ShiftsViewModel(repo, "u", "default")
        waitUntil { vm.shiftTypes.isNotEmpty() }
        vm.openDayModal("2026-09-02")
        vm.toggleDayModalType(vm.shiftTypes.first().id)
        vm.saveDayModal()
        Thread.sleep(300)
        assertTrue(runBlocking { db.shiftDayDao().getAllOnce() }.none { it.dateKey == "2026-09-02" })
    }
}
