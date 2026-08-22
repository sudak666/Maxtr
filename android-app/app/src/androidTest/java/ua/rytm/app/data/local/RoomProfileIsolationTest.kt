package ua.rytm.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ua.rytm.app.data.FinanceRepository

class RoomProfileIsolationTest {
    private lateinit var db: RytmDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RytmDatabase::class.java,
        ).build()
    }

    @After fun close() = db.close()

    @Test fun sameIdsRemainIsolatedAndOffline() = runBlocking {
        RoomProfileScope.activate("owner-a", "default")
        db.walletDao().insert(WalletEntity("shared-id", "A", 1, "UAH"))
        RoomProfileScope.activate("owner-b", "family")
        db.walletDao().insert(WalletEntity("shared-id", "B", 2, "USD"))

        assertEquals(listOf("B"), db.walletDao().getAllOnce().map { it.name })
        assertEquals(listOf("B"), db.walletDao().observeAll().first().map { it.name })

        RoomProfileScope.activate("owner-a", "default")
        assertEquals(listOf("A"), db.walletDao().getAllOnce().map { it.name })
        db.walletDao().clearAll()
        assertEquals(0, db.walletDao().count())

        RoomProfileScope.activate("owner-b", "family")
        assertEquals(listOf("B"), db.walletDao().getAllOnce().map { it.name })
    }

    @Test fun repositoryFlowRebindsWithoutRecreation() = runBlocking {
        RoomProfileScope.activate("owner-a", "default")
        db.walletDao().insert(WalletEntity("wallet", "A", 1, "UAH"))
        RoomProfileScope.activate("owner-b", "family")
        db.walletDao().insert(WalletEntity("wallet", "B", 2, "USD"))
        RoomProfileScope.activate("owner-a", "default")

        val repository = FinanceRepository(db)
        val firstEmission = CompletableDeferred<Unit>()
        val observed = async {
            withTimeout(5_000) {
                repository.wallets.map { rows -> rows.map { it.name } }
                    .onEach { if (it == listOf("A")) firstEmission.complete(Unit) }
                    .distinctUntilChanged().take(2).toList()
            }
        }
        withTimeout(5_000) { firstEmission.await() }
        RoomProfileScope.activate("owner-b", "family")
        assertEquals(listOf(listOf("A"), listOf("B")), observed.await())
    }
}
