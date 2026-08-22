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

// 1:1 with AppState.tags (js/state.js), `[{id,name,color}]` — confirmed by
// reading js/finance.js's addTag()/updateTag(). Transactions reference tags
// by id (TransactionEntity.tags, a comma-joined string of these ids — see
// that entity's own doc comment for why it's not a join table).
@Entity(tableName = "tags", primaryKeys = ["ownerUid", "profileId", "id"])
data class TagEntity(
    val id: String,
    val name: String,
    val colorHex: Long,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY name ASC")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY name ASC")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>)

    // See WalletDao's own update() comment — a real UPDATE preserves rowid/order,
    // unlike INSERT OR REPLACE's delete+insert (which reorders observeAll()'s
    // no-ORDER-BY result — same bug class hit and fixed for wallets/shift types).
    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM tags WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Same "remote wins" cold-sync bootstrap pattern as every other synced domain.
    @Transaction
    suspend fun replaceAll(tags: List<TagEntity>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        clearAll(ownerUid, profileId)
        insertAll(tags)
    }
}
