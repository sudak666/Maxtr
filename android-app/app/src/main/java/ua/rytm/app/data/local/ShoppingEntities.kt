package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// 1:1 with the ShoppingItem typedef in js/state.js — SHOPPING_SCREEN_SPEC.md.
@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val qty: Int,
    val done: Boolean,
    val createdAt: Long,
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<ShoppingItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_items")
    suspend fun clearAll()

    // Same "remote wins" cold-sync bootstrap pattern as WalletDao/CategoryDao's
    // replaceAll() — a real @Transaction so a crash mid-sync can't leave the
    // table half-cleared.
    @Transaction
    suspend fun replaceAll(items: List<ShoppingItemEntity>) {
        clearAll()
        insertAll(items)
    }

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM shopping_items WHERE done = 1")
    suspend fun deleteBought()

    @Query("SELECT COUNT(*) FROM shopping_items")
    suspend fun count(): Int
}
