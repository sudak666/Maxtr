package ua.rytm.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ua.rytm.app.BuildConfig
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.local.AutoRuleEntity
import ua.rytm.app.data.local.BudgetEntity
import ua.rytm.app.data.local.CategoryEntity
import ua.rytm.app.data.local.CurrencyRateEntity
import ua.rytm.app.data.local.GoalEntity
import ua.rytm.app.data.local.RecurringEntity
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.TagEntity
import ua.rytm.app.data.local.WalletEntity
import ua.rytm.app.data.local.clearActiveProfileTables

@RunWith(AndroidJUnit4::class)
class FinanceSyncScopeFirebaseE2eTest {
    private val app = ApplicationProvider.getApplicationContext<RytmApplication>()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var uid: String
    private val target = "finance_target"
    private val other = "finance_private"

    @Before fun setUp() = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATOR)
        uid = auth.signInAnonymously().await().user!!.uid
        RoomProfileScope.activate(uid, target)
        app.database.clearActiveProfileTables()
        RoomProfileScope.activate(uid, other)
        app.database.clearActiveProfileTables()
    }

    @After fun tearDown() = runBlocking {
        RoomProfileScope.activate(uid, target)
        app.database.clearActiveProfileTables()
        RoomProfileScope.activate(uid, other)
        app.database.clearActiveProfileTables()
        auth.currentUser?.delete()?.await()
        auth.signOut()
    }

    @Test fun everyFinanceSnapshotUsesRequestedScopeNotActiveScope() = runBlocking {
        seed(target, "target")
        seed(other, "private")
        RoomProfileScope.activate(uid, other)

        app.financeSyncRepository.saveWalletsSnapshot(uid, target)
        app.categoriesSyncRepository.saveAllCategorySnapshots(uid, target)
        app.budgetsSyncRepository.saveBudgetsSnapshot(uid, target)
        app.tagsSyncRepository.saveTagsSnapshot(uid, target)
        app.recurringSyncRepository.saveRecurringSnapshot(uid, target)
        app.goalsSyncRepository.saveGoalsSnapshot(uid, target)
        app.autoRulesSyncRepository.save(uid, target)
        app.currencyRatesSyncRepository.saveRate(uid, target, "USD", 40.0)

        val serialized = financeRef().get(Source.SERVER).await().data.toString()
        assertTrue(serialized.contains("target"))
        assertFalse(serialized.contains("private"))
    }

    private suspend fun seed(profile: String, marker: String) {
        app.database.walletDao().insert(WalletEntity("wallet-$marker", "Wallet $marker", 0xff8b5cf6, "UAH", "card", uid, profile))
        app.database.categoryDao().insert(CategoryEntity("category-$marker", "EXPENSE", "Category $marker", uid, profile))
        app.database.budgetDao().upsert(BudgetEntity("Category $marker", 100.0, uid, profile))
        app.database.tagDao().insert(TagEntity("tag-$marker", "Tag $marker", 0xff8b5cf6, uid, profile))
        app.database.recurringDao().insert(RecurringEntity("recurring-$marker", "EXPENSE", 10.0, "Category $marker", "wallet-$marker", "monthly", "2026-09-01", true, marker, uid, profile))
        app.database.goalDao().insert(GoalEntity("goal-$marker", "wallet-$marker", 1000.0, "2026-12-31", uid, profile))
        app.database.autoRuleDao().upsert(AutoRuleEntity("rule-$marker", "expense", marker, "Category $marker", 0, uid, profile))
        app.database.currencyRateDao().insertAll(listOf(CurrencyRateEntity("USD", if (marker == "target") 39.0 else 99.0, uid, profile)))
    }

    private fun financeRef() = firestore.collection("users").document(uid)
        .collection("max_tracker").document(profileDocName("finance", target))
}
