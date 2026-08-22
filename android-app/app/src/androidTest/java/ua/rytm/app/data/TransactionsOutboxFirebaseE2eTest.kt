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
import ua.rytm.app.data.local.TransactionEntity
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

    private fun remoteCollection() = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profileId)).collection("transactions")

    private fun financeRef(profile: String) = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", profile))

    private fun transaction(id: String) = TransactionEntity(
        id = id, type = "EXPENSE", amount = 42.5, currency = "UAH", date = "2026-08-22",
        walletId = "cash", targetWalletId = null, targetAmount = null, targetCurrency = null,
        category = "Food", subcategory = null, comment = "offline", tags = "", createdAt = 1L,
        ownerUid = uid, profileId = profileId,
    )
}
