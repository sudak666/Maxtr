package ua.rytm.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.first
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
import ua.rytm.app.data.local.ShoppingItemEntity
import ua.rytm.app.data.local.DebtEntity
import ua.rytm.app.data.local.AutoFillScheduleEntity
import ua.rytm.app.data.local.ShiftDayEntity
import ua.rytm.app.data.local.ShiftTypeEntity
import ua.rytm.app.data.local.TransactionEntity
import ua.rytm.app.data.local.WalletEntity
import ua.rytm.app.data.local.clearActiveProfileTables

@RunWith(AndroidJUnit4::class)
class TransactionsOutboxFirebaseE2eTest {
    private val app = ApplicationProvider.getApplicationContext<RytmApplication>()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var uid: String
    private val profileId = "outbox_e2e"
    private lateinit var repository: TransactionsSyncRepository

    @Before fun setUp() = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATOR) { "Run with -PuseFirebaseEmulator=true" }
        uid = auth.signInAnonymously().await().user!!.uid
        RoomProfileScope.activate(uid, profileId)
        app.database.clearActiveProfileTables()
        repository = TransactionsSyncRepository(app.database, firestore)
    }

    @After fun tearDown() = runBlocking {
        app.database.clearActiveProfileTables()
        auth.currentUser?.delete()?.await()
        auth.signOut()
    }

    @Test fun pendingSaveAndDeleteReachServerAndFailedWriteRemainsRecoverable() = runBlocking {
        val transaction = transaction("tx-ok")
        repository.queueSave(uid, profileId, transaction)
        assertEquals(TransactionSyncState.PENDING, repository.observeOperationStates(uid, profileId).first()[transaction.id])
        assertEquals(transaction.id, app.database.transactionDao().getAllOnce().single().id)

        assertTrue(repository.drainOutbox())
        assertTrue(repository.observeOperationStates(uid, profileId).first().isEmpty())
        assertTrue(remoteCollection().document(transaction.id).get(Source.SERVER).await().exists())

        repository.queueDeletes(uid, profileId, listOf(transaction.id))
        assertTrue(app.database.transactionDao().getAllOnce().isEmpty())
        assertEquals(TransactionSyncState.PENDING, repository.observeOperationStates(uid, profileId).first()[transaction.id])
        assertTrue(repository.drainOutbox())
        assertFalse(remoteCollection().document(transaction.id).get(Source.SERVER).await().exists())

        val deniedProfile = "invalid-profile"
        val denied = transaction("tx-denied")
        repository.queueSave(uid, deniedProfile, denied)
        assertFalse(repository.drainOutbox())
        assertEquals(TransactionSyncState.ERROR, repository.observeOperationStates(uid, deniedProfile).first()[denied.id])
        assertEquals(denied.id, app.database.transactionDao().getAllOnce(uid, deniedProfile).single().id)
        app.database.syncOutboxDao().clearScope(uid, deniedProfile)
    }

    @Test fun concurrentEditKeepsBothVersionsAndConcurrentDeletePreservesRemote() = runBlocking {
        val original = transaction("tx-conflict")
        repository.queueSave(uid, profileId, original)
        assertTrue(repository.drainOutbox())

        val ref = remoteCollection().document(original.id)
        val remoteV1 = ref.get(Source.SERVER).await().data!!
        ref.set(remoteV1 + mapOf("amount" to 99.0, "revision" to 2L, "updatedAt" to 2L)).await()

        val localV1 = app.database.transactionDao().getById(original.id, uid, profileId)!!
        repository.queueSave(uid, profileId, localV1.copy(amount = 55.0))
        assertFalse(repository.drainOutbox())
        assertTrue(repository.drainOutbox())

        val localRows = app.database.transactionDao().getAllOnce(uid, profileId)
        assertEquals(2, localRows.size)
        assertEquals(99.0, localRows.single { it.id == original.id }.amount, 0.0)
        val conflictCopy = localRows.single { it.id != original.id }
        assertEquals(55.0, conflictCopy.amount, 0.0)
        assertEquals(2, remoteCollection().get(Source.SERVER).await().size())

        repository.queueDeletes(uid, profileId, listOf(original.id))
        val remoteV2 = ref.get(Source.SERVER).await().data!!
        ref.set(remoteV2 + mapOf("amount" to 100.0, "revision" to 3L, "updatedAt" to 3L)).await()
        assertTrue(repository.drainOutbox())
        assertEquals(100.0, app.database.transactionDao().getById(original.id, uid, profileId)!!.amount, 0.0)
        assertTrue(ref.get(Source.SERVER).await().exists())
    }

    @Test fun coldSyncDoesNotBypassPendingTransactionRevision() = runBlocking {
        val pending = transaction("tx-cold-pending")
        repository.queueSave(uid, profileId, pending)
        repository.syncTransactionsOnSignIn(uid, profileId)
        assertTrue(repository.drainOutbox())
        assertEquals(1, remoteCollection().get(Source.SERVER).await().size())
        assertTrue(repository.observeOperationStates(uid, profileId).first().isEmpty())
    }

    @Test fun financeSnapshotOutboxSurvivesOfflineAndRejectsStaleOverwrite() = runBlocking {
        val snapshots = FinanceSnapshotOutboxRepository(app.database, firestore)
        val finance = FinanceSyncRepository(app.database, firestore, snapshots)
        app.database.walletDao().insert(WalletEntity("wallet-a", "Local", 1L, "UAH", "card", uid, profileId))

        finance.saveWalletsSnapshot(uid, profileId)
        snapshots.queue(uid, profileId, "budgets", mapOf("Food" to 100.0))
        assertEquals(TransactionSyncState.PENDING, snapshots.observeState(uid, profileId).first())
        assertTrue(snapshots.drainOutbox())
        val firstRemote = financeRef(profileId).get(Source.SERVER).await()
        assertEquals(1L, firstRemote.getLong("fieldRevisions.wallets"))
        assertEquals(1L, firstRemote.getLong("fieldRevisions.budgets"))
        assertEquals(100.0, ((firstRemote.get("budgets") as Map<*, *>)["Food"] as Number).toDouble(), 0.0)

        app.database.walletDao().update(WalletEntity("wallet-a", "Offline edit", 1L, "UAH", "card", uid, profileId))
        finance.saveWalletsSnapshot(uid, profileId)
        financeRef(profileId).set(
            mapOf(
                "wallets" to listOf(mapOf("id" to "wallet-a", "name" to "Remote edit", "color" to "#000001", "currency" to "UAH", "icon" to "card")),
                "fieldRevisions" to mapOf("wallets" to 2L),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()

        finance.syncWalletsOnSignIn(uid, profileId)
        assertEquals("Offline edit", app.database.walletDao().getAllOnce(uid, profileId).single().name)
        assertFalse(snapshots.drainOutbox())
        assertEquals(TransactionSyncState.ERROR, snapshots.observeState(uid, profileId).first())
        assertEquals("Remote edit", ((financeRef(profileId).get(Source.SERVER).await().get("wallets") as List<*>).single() as Map<*, *>)["name"])
    }

    @Test fun shoppingSnapshotSurvivesProfileSwitchWithoutCrossProfileLeak() = runBlocking {
        val shopping = ShoppingSyncRepository(app.database, firestore)
        shopping.queueSnapshot(uid, profileId) {
            app.database.shoppingDao().upsert(
                ShoppingItemEntity("shop-a", "Milk", 2, false, 1L, uid, profileId),
            )
        }

        val otherProfile = "other_profile"
        RoomProfileScope.activate(uid, otherProfile)
        app.database.shoppingDao().upsert(
            ShoppingItemEntity("shop-secret", "Other profile", 1, false, 2L, uid, otherProfile),
        )
        assertTrue(shopping.drainOutbox())

        val remote = financeRef(profileId).get(Source.SERVER).await().get("shoppingList") as List<*>
        val ids = remote.map { (it as Map<*, *>)["id"] }
        assertEquals(listOf("shop-a"), ids)
        RoomProfileScope.activate(uid, profileId)
        app.database.shoppingDao().clearAll(uid, otherProfile)
    }

    @Test fun debtSnapshotSurvivesProfileSwitchWithoutCrossProfileLeak() = runBlocking {
        val debtSync = DebtSyncRepository(app.database, firestore)
        debtSync.queueSnapshot(uid, profileId, 11L) {
            app.database.debtDao().insert(DebtEntity(11L, "Primary debt", "", "UAH", 100.0, "", uid, profileId))
        }

        val otherProfile = "other_profile"
        RoomProfileScope.activate(uid, otherProfile)
        app.database.debtDao().insert(DebtEntity(22L, "Private debt", "", "UAH", 200.0, "", uid, otherProfile))
        assertTrue(debtSync.drainOutbox())

        val data = debtRef(profileId).get(Source.SERVER).await().get("data") as Map<*, *>
        val remoteDebts = data["debts"] as List<*>
        assertEquals(listOf(11L), remoteDebts.map { ((it as Map<*, *>)["id"] as Number).toLong() })
        assertEquals(11L, (data["currentDebtId"] as Number).toLong())
        RoomProfileScope.activate(uid, profileId)
        app.database.debtDao().clearAll(uid, otherProfile)
    }

    @Test fun shiftsSnapshotSurvivesProfileSwitchWithoutCrossProfileLeak() = runBlocking {
        val shiftsSync = ShiftsSyncRepository(app.database, firestore)
        shiftsSync.queueSnapshot(uid, profileId) {
            app.database.shiftTypeDao().insert(ShiftTypeEntity("work", "Work", "W", "W", 0xff0000, 100.0, 8.0, false, uid, profileId))
            app.database.shiftDayDao().insertAll(listOf(ShiftDayEntity("2026-08-22", "work", uid, profileId)))
            app.database.autoFillScheduleDao().upsert(AutoFillScheduleEntity(0, false, "", "every", "", uid, profileId))
        }

        val otherProfile = "other_profile"
        RoomProfileScope.activate(uid, otherProfile)
        app.database.shiftTypeDao().insert(ShiftTypeEntity("private", "Private", "P", "P", 0x00ff00, 200.0, 8.0, false, uid, otherProfile))
        assertTrue(shiftsSync.drainOutbox())

        val remote = shiftsRef(profileId).get(Source.SERVER).await()
        val typeIds = (remote.get("shiftTypes") as List<*>).map { (it as Map<*, *>)["id"] }
        assertEquals(listOf("work"), typeIds)
        val days = remote.get("data") as Map<*, *>
        assertEquals(listOf("work"), days["2026-08-22"])
        RoomProfileScope.activate(uid, profileId)
        app.database.shiftTypeDao().clearAll(uid, otherProfile)
    }

    private fun remoteCollection() = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profileId)).collection("transactions")

    private fun financeRef(profile: String) = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profile))

    private fun debtRef(profile: String) = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("debt", profile))

    private fun shiftsRef(profile: String) = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("shifts", profile))

    private fun transaction(id: String) = TransactionEntity(
        id = id, type = "EXPENSE", amount = 42.5, currency = "UAH", date = "2026-08-22",
        walletId = "cash", targetWalletId = null, targetAmount = null, targetCurrency = null,
        category = "Food", subcategory = null, comment = "offline", tags = "", createdAt = 1L,
        ownerUid = uid, profileId = profileId,
    )
}
