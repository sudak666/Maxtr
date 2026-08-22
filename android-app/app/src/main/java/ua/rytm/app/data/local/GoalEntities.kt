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

// 1:1 with AppState.goals (js/state.js), `[{id,walletId,targetAmount,
// targetDate}]` — confirmed by reading js/goals-profile.js's
// confirmAddGoal()/updateGoal(). Progress is deliberately NOT stored here:
// the PWA computes it live as `computeWalletBalances()[walletId]` against
// `targetAmount` (a goal tracks a wallet's current balance, not a separate
// accumulator) — see FinanceRepository.walletBalance(), already used
// identically for the wallet chips on the Finance hero card.
@Entity(tableName = "goals", primaryKeys = ["ownerUid", "profileId", "id"])
data class GoalEntity(
    val id: String,
    val walletId: String,
    val targetAmount: Double,
    val targetDate: String, // free-text, e.g. "Грудень 2026" — matches the PWA's plain text input, not a real date type
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE ownerUid=:ownerUid AND profileId=:profileId")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM goals WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Transaction
    suspend fun replaceAll(goals: List<GoalEntity>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        clearAll(ownerUid, profileId)
        insertAll(goals)
    }
}
