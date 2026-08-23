package ua.rytm.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ua.rytm.app.BuildConfig
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.TransactionEntity
import ua.rytm.app.data.local.WalletEntity
import ua.rytm.app.data.local.clearActiveProfileTables

@RunWith(AndroidJUnit4::class)
class ProfileRestoreFirebaseE2eTest {
    private val app = ApplicationProvider.getApplicationContext<RytmApplication>()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var uid: String
    private val profileId = "restore_e2e"

    @Before fun setUp() = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATOR) { "Run with -PuseFirebaseEmulator=true" }
        uid = auth.signInAnonymously().await().user!!.uid
        RoomProfileScope.activate(uid, profileId)
        app.database.clearActiveProfileTables()
    }

    @After fun tearDown() = runBlocking {
        app.profileSyncCoordinator.stopRealtimeSync()
        app.database.clearActiveProfileTables()
        auth.currentUser?.delete()?.await()
        auth.signOut()
    }

    @Test fun restoreReplacesLocalAndRemoteProfileThroughDurableOutboxes() = runBlocking {
        app.database.walletDao().insert(WalletEntity("restored-wallet", "Restored", 0x123456, "UAH", ownerUid = uid, profileId = profileId))
        app.database.transactionDao().upsert(transaction("restored-tx", "restored-wallet", "Restored transaction"))
        val payload = ProfileBackupRepository(app.database).export("correct horse".toCharArray())

        app.database.clearActiveProfileTables()
        app.database.walletDao().insert(WalletEntity("old-wallet", "Old", 0x654321, "USD", ownerUid = uid, profileId = profileId))
        financeRef().set(mapOf("wallets" to listOf(mapOf("id" to "old-wallet", "name" to "Old")))).await()
        remoteTransactions().document("old-tx").set(
            mapOf(
                "type" to "expense", "amount" to 1.0, "currency" to "UAH",
                "date" to "2026-08-23", "wallet" to "old-wallet", "revision" to 1L,
            ),
        ).await()

        val password = "correct horse".toCharArray()
        val preview = app.profileSyncCoordinator.restoreOwnProfile(uid, profileId, null, payload, password)
        assertEquals(2, preview.rowCount)
        assertEquals("Restored", app.database.walletDao().getAllOnce(uid, profileId).single().name)
        assertEquals("restored-tx", app.database.transactionDao().getAllOnce(uid, profileId).single().id)
        assertTrue(app.database.syncOutboxDao().countForScope(uid, profileId) > 0)

        assertTrue(app.financeSnapshotOutboxRepository.drainOutbox())
        assertTrue(app.shoppingSyncRepository.drainOutbox())
        assertTrue(app.debtSyncRepository.drainOutbox())
        assertTrue(app.shiftsSyncRepository.drainOutbox())
        assertTrue(app.transactionsSyncRepository.drainOutbox())

        val wallets = financeRef().get(Source.SERVER).await().get("wallets") as List<*>
        assertEquals("Restored", (wallets.single() as Map<*, *>)["name"])
        assertTrue(remoteTransactions().document("restored-tx").get(Source.SERVER).await().exists())
        assertFalse(remoteTransactions().document("old-tx").get(Source.SERVER).await().exists())
        assertEquals(0, app.database.syncOutboxDao().countForScope(uid, profileId))

        payload.fill(0)
        assertTrue(password.all { it == '\u0000' })
    }

    private fun financeRef() = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profileId))

    private fun remoteTransactions() = financeRef().collection("transactions")

    private fun transaction(id: String, walletId: String, comment: String) = TransactionEntity(
        id = id, type = "EXPENSE", amount = 42.5, currency = "UAH", date = "2026-08-23",
        walletId = walletId, targetWalletId = null, targetAmount = null, targetCurrency = null,
        category = "Food", subcategory = null, comment = comment, tags = "", createdAt = 1L,
        ownerUid = uid, profileId = profileId,
    )
}
