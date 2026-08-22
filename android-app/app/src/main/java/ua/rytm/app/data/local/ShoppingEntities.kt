package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// 1:1 with the ShoppingItem typedef in js/state.js — SHOPPING_SCREEN_SPEC.md.
@Entity(tableName = "shopping_items", primaryKeys = ["ownerUid", "profileId", "id"])
data class ShoppingItemEntity(
    val id: String,
    val name: String,
    val qty: Int,
    val done: Boolean,
    val createdAt: Long,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY createdAt ASC")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY createdAt ASC")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_items WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Same "remote wins" cold-sync bootstrap pattern as WalletDao/CategoryDao's
    // replaceAll() — a real @Transaction so a crash mid-sync can't leave the
    // table half-cleared.
    @Transaction
    suspend fun replaceAll(items: List<ShoppingItemEntity>) {
        clearAll()
        insertAll(items)
    }

    @Query("DELETE FROM shopping_items WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM shopping_items WHERE ownerUid=:ownerUid AND profileId=:profileId AND done = 1")
    suspend fun deleteBought(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("SELECT COUNT(*) FROM shopping_items WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun count(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int
}
