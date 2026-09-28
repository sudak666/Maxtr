package ua.rytm.app.ui

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ua.rytm.app.R
import ua.rytm.app.data.ProfileMeta
import ua.rytm.app.data.ProfilesRepository
import ua.rytm.app.data.RedeemInviteResult
import ua.rytm.app.data.local.ActiveProfileStore
import ua.rytm.app.ui.screens.ProfilesManagerViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ProfilesManagerViewModelTest {
    private val own = ProfileMeta("default", "Me", 1)
    private val second = ProfileMeta("p2", "Wife", 2)
    private val shared = ProfileMeta("default", "Shared", 3, kind = "shared", ownerUid = "owner")
    private val repo = mockk<ProfilesRepository>(relaxed = true)
    private var switchedTo: Triple<String, String, String?>? = null
    private var switchFails = false

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repo.list("u") } returns listOf(own, second, shared)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun waitUntil(cond: () -> Boolean) { repeat(150) { if (cond()) return; Thread.sleep(20) } }

    private fun vm() = ProfilesManagerViewModel(
        repo,
        ActiveProfileStore(ApplicationProvider.getApplicationContext()),
        { uid, id, owner -> if (switchFails) throw RuntimeException("offline"); switchedTo = Triple(uid, id, owner) },
        "u",
    ).also { v -> waitUntil { v.profiles.size == 3 } }

    @Test fun activeProfileCannotBeDeleted() {
        val vm = vm()
        vm.requestDelete(own)
        assertEquals(R.string.profile_delete_active_error, vm.errorMessageRes)
        assertNull(vm.pendingDeleteId)
    }

    @Test fun deleteCallsRepositoryAndReloads() {
        val vm = vm()
        vm.requestDelete(second)
        vm.confirmDelete()
        Thread.sleep(200)
        coVerify { repo.deleteProfile("u", "p2") }
        coVerify(atLeast = 2) { repo.list("u") }
    }

    // A shared profile with the same id as this account's own active one is
    // a different row — switching to it must pass the owner's uid.
    @Test fun switchToSharedPassesOwnerUid() {
        val vm = vm()
        vm.requestSwitch(shared)
        assertTrue(runBlocking { vm.confirmSwitch() })
        assertEquals(Triple("u", "default", "owner"), switchedTo)
    }

    @Test fun failedSwitchReturnsFalseWithError() {
        switchFails = true
        val vm = vm()
        vm.requestSwitch(second)
        assertFalse(runBlocking { vm.confirmSwitch() })
        assertEquals(R.string.profile_switch_failed, vm.errorMessageRes)
        assertFalse(vm.switching)
    }

    @Test fun joinByCodeMapsFailureReasons() {
        val vm = vm()
        mapOf(
            "own-profile" to R.string.profile_join_own_error,
            "used" to R.string.profile_join_used_error,
            "expired" to R.string.profile_join_expired_error,
            "not-found" to R.string.profile_join_not_found_error,
        ).forEach { (reason, res) ->
            coEvery { repo.redeemInvite("u", "CODE1234") } returns RedeemInviteResult.Failed(reason)
            vm.consumeError()
            vm.joinByCode(" CODE1234 ")
            waitUntil { vm.errorMessageRes != null && !vm.joining }
            assertEquals(reason, res, vm.errorMessageRes)
        }
    }

    @Test fun onlySharedProfilesCanBeLeft() {
        val vm = vm()
        vm.requestLeave(second)
        assertNull(vm.pendingLeave)
        vm.requestLeave(shared)
        assertEquals(shared, vm.pendingLeave)
        vm.confirmLeave()
        Thread.sleep(200)
        coVerify { repo.leaveSharedProfile("u", "owner", "default") }
    }
}
