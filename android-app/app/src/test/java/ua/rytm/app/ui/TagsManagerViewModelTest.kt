package ua.rytm.app.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import ua.rytm.app.data.TagsSyncRepository
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.ui.screens.finance.TagsManagerViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TagsManagerViewModelTest {
    private lateinit var db: RytmDatabase
    private lateinit var repo: FinanceRepository
    private val sync = mockk<TagsSyncRepository>(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RytmDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FinanceRepository(db)
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    // 3 s, not 1 s: a loaded CI runner missed the rename within 1 s (flaky on main, 2026-10-06).
    private fun waitUntil(cond: () -> Boolean) { repeat(150) { if (cond()) return; Thread.sleep(20) } }

    private suspend fun awaitTags(expected: Int) = waitUntil { kotlinx.coroutines.runBlocking { db.tagDao().getAllOnce().size == expected } }

    @Test fun addRenameDeleteSyncEachTime() = runTest {
        val vm = TagsManagerViewModel(repo, sync, "u", "default")
        vm.addTag("  trip  ")
        awaitTags(1)
        val tag = db.tagDao().getAllOnce().single()
        assertEquals("trip", tag.name)

        vm.renameTag(ua.rytm.app.ui.screens.finance.Tag(tag.id, tag.name, tag.colorHex), "travel")
        waitUntil { kotlinx.coroutines.runBlocking { db.tagDao().getAllOnce().single().name == "travel" } }
        assertEquals("travel", db.tagDao().getAllOnce().single().name)

        vm.requestDelete(tag.id); vm.confirmDelete()
        awaitTags(0)
        assertTrue(db.tagDao().getAllOnce().isEmpty())
        coVerify(atLeast = 3) { sync.saveTagsSnapshot("u", "default") }
    }

    @Test fun failedSyncRollsBackAndReportsError() = runTest {
        coEvery { sync.saveTagsSnapshot(any(), any()) } throws RuntimeException("offline")
        val vm = TagsManagerViewModel(repo, sync, "u", "default")
        vm.addTag("trip")
        waitUntil { vm.errorMessageRes != null }
        assertEquals(R.string.tags_save_failed, vm.errorMessageRes)
        assertTrue(db.tagDao().getAllOnce().isEmpty())
    }

    @Test fun blankNameIsIgnored() = runTest {
        val vm = TagsManagerViewModel(repo, sync, "u", "default")
        vm.addTag("   ")
        assertTrue(db.tagDao().getAllOnce().isEmpty())
        coVerify(exactly = 0) { sync.saveTagsSnapshot(any(), any()) }
    }
}
