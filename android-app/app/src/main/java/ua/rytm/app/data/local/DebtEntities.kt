package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// 1:1 with js/debt.js's Debt/DebtEntry typedefs. id is a Long timestamp
// (System.currentTimeMillis()), same convention as the PWA's Date.now().
@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val note: String,
    val currency: String,
    val startAmount: Double,
    val dueDate: String,
)

// amount is a free-form string in the PWA (not always a plain number — see
// updateDebtEntry()'s isCleanNumber discrepancy check), so it stays a String
// here too rather than being coerced to Double.
@Entity(tableName = "debt_entries")
data class DebtEntryEntity(
    @PrimaryKey val id: Long,
    val debtId: Long,
    val amount: String,
    val balance: Double,
    val date: String,
)

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts")
    suspend fun getAllOnce(): List<DebtEntity>

    @Insert
    suspend fun insert(debt: DebtEntity)

    @Insert
    suspend fun insertAll(debts: List<DebtEntity>)

    // See ShiftTypeDao's update()/WalletDao's update() comment — a real UPDATE
    // preserves rowid/list order, unlike INSERT OR REPLACE.
    @Update
    suspend fun update(debt: DebtEntity)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM debts")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun count(): Int
}

@Dao
interface DebtEntryDao {
    @Query("SELECT * FROM debt_entries")
    fun observeAll(): Flow<List<DebtEntryEntity>>

    @Query("SELECT * FROM debt_entries")
    suspend fun getAllOnce(): List<DebtEntryEntity>

    @Insert
    suspend fun insert(entry: DebtEntryEntity)

    @Insert
    suspend fun insertAll(entries: List<DebtEntryEntity>)

    @Update
    suspend fun update(entry: DebtEntryEntity)

    @Query("DELETE FROM debt_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM debt_entries")
    suspend fun clearAll()

    // Mirrors js/debt.js's deleteCurrentDebt() implicitly orphaning entries
    // via array filtering — here it's an explicit cascade since Room doesn't
    // auto-cascade without a declared foreign key.
    @Query("DELETE FROM debt_entries WHERE debtId = :debtId")
    suspend fun deleteAllForDebt(debtId: Long)
}
