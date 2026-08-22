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
    @Query("SELECT * FROM wallets")
    fun observeAll(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets")
    suspend fun getAllOnce(): List<WalletEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wallets: List<WalletEntity>)

    @Query("DELETE FROM wallets")
    suspend fun clearAll()

    // Used only by the Firestore cold-sync bootstrap (FinanceSyncRepository) to
    // replace the whole local table with the remote-wins copy — a real @Transaction
    // so a crash mid-sync can't leave the table half-cleared.
    @Transaction
    suspend fun replaceAll(wallets: List<WalletEntity>) {
        clearAll()
        insertAll(wallets)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: WalletEntity)

    // Insert-with-REPLACE does a delete+insert under the hood, changing the row's SQLite
    // rowid and reordering observeAll()'s no-ORDER-BY result (found while testing the
    // identical bug in ShiftTypeDao — renaming a wallet moved it to the bottom of the list).
    @Update
    suspend fun update(wallet: WalletEntity)

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("SELECT monobankId FROM transactions WHERE monobankId IS NOT NULL")
    suspend fun getAllMonobankIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()

    // Same "remote wins" cold-sync bootstrap pattern as WalletDao/CategoryDao's
    // replaceAll() — a real @Transaction so a crash mid-sync can't leave the
    // table half-cleared. Row identity here is the tx id itself (not rowid),
    // so unlike Wallet/ShiftType this doesn't need REPLACE-vs-UPDATE care.
    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>) {
        clearAll()
        insertAll(transactions)
    }

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE walletId = :walletId OR targetWalletId = :walletId")
    suspend fun countUsingWallet(walletId: String): Int
}
