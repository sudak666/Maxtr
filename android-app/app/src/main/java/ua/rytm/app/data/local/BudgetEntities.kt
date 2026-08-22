package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// 1:1 with AppState.budgets (js/state.js) — `Record<expenseCategoryName, number>`,
// confirmed by reading js/settings-managers.js's updateBudget() (a budget row
// is deleted outright when the amount drops to <=0, never stored as a zero —
// same convention followed here). Keyed by category name only, no type
// prefix needed — the PWA only ever lets a budget target an EXPENSE category
// (js/settings-managers.js's openBudgetsManager() only lists
// AppState.categories.expense), so there's no income/expense name-collision
// risk the way subcategories' subKey() has to guard against.
@Entity(tableName = "budgets", primaryKeys = ["ownerUid", "profileId", "category"])
data class BudgetEntity(
    val category: String,
    val amount: Double,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY category ASC")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY category ASC")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE ownerUid=:ownerUid AND profileId=:profileId AND category = :category")
    suspend fun deleteByCategory(category: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM budgets WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Same "remote wins" cold-sync bootstrap pattern as every other synced domain.
    @Transaction
    suspend fun replaceAll(budgets: List<BudgetEntity>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        clearAll(ownerUid, profileId)
        insertAll(budgets)
    }

    // Mirrors js/settings-managers.js's renameCategory() moving
    // AppState.budgets[oldName] to the new key when a budget existed.
    @Query("UPDATE budgets SET category = :newName WHERE ownerUid=:ownerUid AND profileId=:profileId AND category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)
}
