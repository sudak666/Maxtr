package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "auto_rules", primaryKeys = ["ownerUid", "profileId", "id"])
data class AutoRuleEntity(
    val id: String,
    val type: String,
    val keyword: String,
    val category: String,
    val position: Int,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface AutoRuleDao {
    @Query("SELECT * FROM auto_rules WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY position ASC") fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<AutoRuleEntity>>
    @Query("SELECT * FROM auto_rules WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY position ASC") suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<AutoRuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rule: AutoRuleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rules: List<AutoRuleEntity>)
    @Query("DELETE FROM auto_rules WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id") suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)
    @Query("DELETE FROM auto_rules WHERE ownerUid=:ownerUid AND profileId=:profileId") suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)
    @Transaction suspend fun replaceAll(rules: List<AutoRuleEntity>) { clearAll(); insertAll(rules) }
}
