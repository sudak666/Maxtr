package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// 1:1 with js/debt.js's Debt/DebtEntry typedefs. id is a Long timestamp
// (System.currentTimeMillis()), same convention as the PWA's Date.now().
@Entity(tableName = "debts", primaryKeys = ["ownerUid", "profileId", "id"])
data class DebtEntity(
    val id: Long,
    val name: String,
    val note: String,
    val currency: String,
    val startAmount: Double,
    val dueDate: String,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

// amount is a free-form string in the PWA (not always a plain number — see
// updateDebtEntry()'s isCleanNumber discrepancy check), so it stays a String
// here too rather than being coerced to Double.
@Entity(tableName = "debt_entries", primaryKeys = ["ownerUid", "profileId", "id"])
data class DebtEntryEntity(
    val id: Long,
    val debtId: Long,
    val amount: String,
    val balance: Double,
    val date: String,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE ownerUid=:ownerUid AND profileId=:profileId")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<DebtEntity>

    @Insert
    suspend fun insert(debt: DebtEntity)

    @Insert
    suspend fun insertAll(debts: List<DebtEntity>)

    // See ShiftTypeDao's update()/WalletDao's update() comment — a real UPDATE
    // preserves rowid/list order, unlike INSERT OR REPLACE.
    @Update
    suspend fun update(debt: DebtEntity)

    @Query("DELETE FROM debts WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun deleteById(id: Long, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM debts WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("SELECT COUNT(*) FROM debts WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun count(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int
}

@Dao
interface DebtEntryDao {
    @Query("SELECT * FROM debt_entries WHERE ownerUid=:ownerUid AND profileId=:profileId")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<DebtEntryEntity>>

    @Query("SELECT * FROM debt_entries WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<DebtEntryEntity>

    @Insert
    suspend fun insert(entry: DebtEntryEntity)

    @Insert
    suspend fun insertAll(entries: List<DebtEntryEntity>)

    @Update
    suspend fun update(entry: DebtEntryEntity)

    @Query("DELETE FROM debt_entries WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun deleteById(id: Long, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM debt_entries WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Mirrors js/debt.js's deleteCurrentDebt() implicitly orphaning entries
    // via array filtering — here it's an explicit cascade since Room doesn't
    // auto-cascade without a declared foreign key.
    @Query("DELETE FROM debt_entries WHERE ownerUid=:ownerUid AND profileId=:profileId AND debtId = :debtId")
    suspend fun deleteAllForDebt(debtId: Long, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)
}
