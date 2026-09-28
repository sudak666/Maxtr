package ua.rytm.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.rytm.app.data.local.RecurringEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.clearAllProfileScopedTables
import java.time.LocalDate

/** Real Room (in-memory) under Robolectric — covers repository logic, not just pure helpers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomRepositoryTest {
    private lateinit var db: RytmDatabase
    private lateinit var repo: FinanceRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RytmDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FinanceRepository(db)
    }

    @After fun tearDown() = db.close()

    private fun recurring(id: String, next: LocalDate, amount: Double = 50.0, active: Boolean = true, frequency: String = "daily") =
        RecurringEntity(id, "EXPENSE", amount, "Food", "w1", frequency, next.toString(), active, "")

    @Test fun processRecurringMaterializesEachDueDateOnceAndAdvances() = runTest {
        val today = LocalDate.now()
        db.recurringDao().insert(recurring("r1", today.minusDays(2)))
        val created = repo.processRecurring()
        assertEquals(3, created.size) // today-2, today-1, today
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today).map { it.toString() }, created.map { it.date })
        assertEquals(today.plusDays(1).toString(), db.recurringDao().getAllOnce().single().nextDate)
        assertEquals(3, db.transactionDao().getAllOnce().size)
        // Regression (2026-09-28): the caller now pushes `created`; a second
        // pass must not create anything again.
        assertTrue(repo.processRecurring().isEmpty())
        assertEquals(3, db.transactionDao().getAllOnce().size)
    }

    @Test fun inactiveAndZeroAmountRecurringAreSkipped() = runTest {
        val today = LocalDate.now()
        db.recurringDao().insert(recurring("off", today, active = false))
        db.recurringDao().insert(recurring("zero", today, amount = 0.0))
        assertTrue(repo.processRecurring().isEmpty())
        assertEquals(today.toString(), db.recurringDao().getAllOnce().first { it.id == "zero" }.nextDate)
    }

    @Test fun monthlyRecurringAdvancesOneMonth() = runTest {
        val today = LocalDate.now()
        db.recurringDao().insert(recurring("m", today, frequency = "monthly"))
        assertEquals(1, repo.processRecurring().size)
        assertEquals(today.plusMonths(1).toString(), db.recurringDao().getAllOnce().single().nextDate)
    }

    // The cross-account leak fix relies on this wiping every profile-scoped table.
    @Test fun clearAllProfileScopedTablesLeavesNothingBehind() = runTest {
        repo.addTag("trip", 0xFF00FF00)
        db.recurringDao().insert(recurring("r", LocalDate.now()))
        repo.processRecurring()
        db.clearAllProfileScopedTables()
        assertTrue(db.tagDao().getAllOnce().isEmpty())
        assertTrue(db.recurringDao().getAllOnce().isEmpty())
        assertTrue(db.transactionDao().getAllOnce().isEmpty())
    }
}
