package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// 1:1 with AppState.recurring (js/state.js), `[{id,type,amount,category,
// wallet,frequency,nextDate,active,comment}]` — confirmed by reading
// js/settings-managers.js's addRecurring()/updateRecurring() and
// js/color-picker.js's processRecurring(). `type` stored uppercase
// (TxType.name), same convention as TransactionEntity/CategoryEntity —
// RecurringSyncRepository translates to/from the PWA's lowercase
// "income"/"expense" at the Firestore boundary, same as
// CategoriesSyncRepository already does for categories/subcategories.
// `type` is deliberately never TRANSFER — the PWA's own updateRecurring()
// only offers income/expense in its type <select>.
@Entity(tableName = "recurring", primaryKeys = ["ownerUid", "profileId", "id"])
data class RecurringEntity(
    val id: String,
    val type: String, // TxType.name, INCOME or EXPENSE only
    val amount: Double,
    val category: String,
    val walletId: String,
    val frequency: String, // "daily" | "weekly" | "monthly"
    val nextDate: String, // "yyyy-MM-dd"
    val active: Boolean,
    val comment: String,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY nextDate ASC")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY nextDate ASC")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<RecurringEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurring: RecurringEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recurring: List<RecurringEntity>)

    // See WalletDao's own update() comment — a real UPDATE preserves rowid/order,
    // unlike INSERT OR REPLACE's delete+insert (which reorders observeAll()'s
    // no-ORDER-BY result — same bug class hit and fixed for wallets/shift types).
    // Not strictly load-bearing here since this DAO orders by nextDate, but kept
    // consistent with every other manager's update path.
    @Update
    suspend fun update(recurring: RecurringEntity)

    @Query("DELETE FROM recurring WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM recurring WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Mirrors js/settings-managers.js's walletInUse(): `AppState.transactions.some(...)
    // || AppState.recurring.some(r=>r.wallet===id)` — see FinanceRepository.isWalletInUse().
    @Query("SELECT COUNT(*) FROM recurring WHERE ownerUid=:ownerUid AND profileId=:profileId AND walletId = :id")
    suspend fun countUsingWallet(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int

    // Same "remote wins" cold-sync bootstrap pattern as every other synced domain.
    @Transaction
    suspend fun replaceAll(recurring: List<RecurringEntity>) {
        clearAll()
        insertAll(recurring)
    }

    // Mirrors js/settings-managers.js's renameCategory(), which reaches into
    // AppState.recurring too (line: `AppState.recurring.forEach(r=>{ if(r.type
    // ===AppState.catMgrType && r.category===oldName) r.category=newName; })`)
    // — confirmed by reading it, not guessed. deleteCategory() deliberately does
    // NOT cascade into recurring (only budgets/subcategories/categoryIcons), so
    // there's no matching delete-cascade query here — see FinanceRepository.deleteCategory().
    @Query("UPDATE recurring SET category = :newName WHERE ownerUid=:ownerUid AND profileId=:profileId AND type = :type AND category = :oldName")
    suspend fun renameCategory(type: String, oldName: String, newName: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)
}
