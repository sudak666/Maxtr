package ua.rytm.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.TransactionEntity
import ua.rytm.app.data.local.WalletEntity

class ProfileBackupRepositoryTest {
    private lateinit var db: RytmDatabase
    private lateinit var repository: ProfileBackupRepository

    @Before fun setUp() {
        RoomProfileScope.activate("source-owner", "source-profile")
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RytmDatabase::class.java).build()
        repository = ProfileBackupRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun encryptedRoundTripRestoresIntoCurrentScope() = runBlocking {
        db.walletDao().insert(WalletEntity("wallet", "Card", 1, "UAH"))
        db.transactionDao().upsert(TransactionEntity("tx", "EXPENSE", 42.5, "UAH", "2026-08-22", "wallet", null, null, null, "Food", null, "kept", "", 1))
        val backup = repository.export("correct horse".toCharArray())

        RoomProfileScope.activate("target-owner", "target-profile")
        assertEquals(2, repository.restore(backup, "correct horse".toCharArray()))
        assertEquals("Card", db.walletDao().getAllOnce().single().name)
        assertEquals("target-owner", db.walletDao().getAllOnce().single().ownerUid)
        assertEquals("kept", db.transactionDao().getAllOnce().single().comment)

        RoomProfileScope.activate("source-owner", "source-profile")
        assertEquals("Card", db.walletDao().getAllOnce().single().name)
    }

    @Test fun invalidBackupNeverMutatesCurrentData() = runBlocking {
        db.walletDao().insert(WalletEntity("wallet", "Original", 1, "UAH"))
        val backup = repository.export("correct horse".toCharArray())
        backup[backup.lastIndex] = (backup.last().toInt() xor 1).toByte()
        assertThrows(InvalidBackupException::class.java) {
            runBlocking { repository.restore(backup, "correct horse".toCharArray()) }
        }
        assertEquals("Original", db.walletDao().getAllOnce().single().name)
    }
}
