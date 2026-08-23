package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets WHERE ownerUid = :ownerUid AND profileId = :profileId")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE ownerUid = :ownerUid AND profileId = :profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<WalletEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wallets: List<WalletEntity>)

    @Query("DELETE FROM wallets WHERE ownerUid = :ownerUid AND profileId = :profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Used only by the Firestore cold-sync bootstrap (FinanceSyncRepository) to
    // replace the whole local table with the remote-wins copy — a real @Transaction
    // so a crash mid-sync can't leave the table half-cleared.
    @Transaction
    suspend fun replaceAll(wallets: List<WalletEntity>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        clearAll(ownerUid, profileId)
        insertAll(wallets)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: WalletEntity)

    // Insert-with-REPLACE does a delete+insert under the hood, changing the row's SQLite
    // rowid and reordering observeAll()'s no-ORDER-BY result (found while testing the
    // identical bug in ShiftTypeDao — renaming a wallet moved it to the bottom of the list).
    @Update
    suspend fun update(wallet: WalletEntity)

    @Query("DELETE FROM wallets WHERE ownerUid = :ownerUid AND profileId = :profileId AND id = :id")
    suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("SELECT COUNT(*) FROM wallets WHERE ownerUid = :ownerUid AND profileId = :profileId")
    suspend fun count(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE ownerUid = :ownerUid AND profileId = :profileId ORDER BY date DESC, createdAt DESC")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE ownerUid = :ownerUid AND profileId = :profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE ownerUid = :ownerUid AND profileId = :profileId AND id = :id LIMIT 1")
    suspend fun getById(id: String, ownerUid: String, profileId: String): TransactionEntity?

    @Query("SELECT monobankId FROM transactions WHERE ownerUid = :ownerUid AND profileId = :profileId AND monobankId IS NOT NULL")
    suspend fun getAllMonobankIds(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE ownerUid = :ownerUid AND profileId = :profileId AND id = :id")
    suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM transactions WHERE ownerUid = :ownerUid AND profileId = :profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("UPDATE transactions SET category = :newName WHERE ownerUid = :ownerUid AND profileId = :profileId AND type = :type AND category = :oldName")
    suspend fun renameCategory(type: String, oldName: String, newName: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Same "remote wins" cold-sync bootstrap pattern as WalletDao/CategoryDao's
    // replaceAll() — a real @Transaction so a crash mid-sync can't leave the
    // table half-cleared. Row identity here is the tx id itself (not rowid),
    // so unlike Wallet/ShiftType this doesn't need REPLACE-vs-UPDATE care.
    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        clearAll(ownerUid, profileId)
        insertAll(transactions)
    }

    @Query("SELECT COUNT(*) FROM transactions WHERE ownerUid = :ownerUid AND profileId = :profileId")
    suspend fun count(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE ownerUid = :ownerUid AND profileId = :profileId AND (walletId = :walletId OR targetWalletId = :walletId)")
    suspend fun countUsingWallet(walletId: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int
}
