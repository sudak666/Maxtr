package ua.rytm.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// 1:1 with AppState.categories[type] (js/state.js) — flat name list per
// TxType, no subcategories/icons/budgets ported yet (see
// js/settings-managers.js's deleteCategory() cascade, intentionally not
// mirrored here).
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val type: String, // TxType.name ("INCOME" or "EXPENSE")
    val name: String,
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun clearAll()

    // Same "remote wins" cold-sync bootstrap pattern as WalletDao.replaceAll() —
    // a real @Transaction so a crash mid-sync can't leave the table half-cleared.
    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>) {
        clearAll()
        insertAll(categories)
    }

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE type = :type AND name = :name")
    suspend fun countByTypeAndName(type: String, name: String): Int
}

// 1:1 with AppState.subcategories (js/state.js) — `Record<subKey(type,name), string[]>`
// (js/core.js's subKey(type,name) => `${type}:${name}`, confirmed by reading
// js/settings-managers.js's addSubcategory()/deleteSubcategory()). Composite
// key here (no separate id) since the PWA itself has no id concept for these
// either — same reasoning CategoriesSyncRepository's doc comment already gives
// for CategoryEntity.id being a purely local Room artifact.
@Entity(tableName = "subcategories", primaryKeys = ["categoryType", "categoryName", "name"])
data class SubcategoryEntity(
    val categoryType: String, // TxType.name
    val categoryName: String,
    val name: String,
)

@Dao
interface SubcategoryDao {
    @Query("SELECT * FROM subcategories ORDER BY name ASC")
    fun observeAll(): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM subcategories ORDER BY name ASC")
    suspend fun getAllOnce(): List<SubcategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(subcategory: SubcategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(subcategories: List<SubcategoryEntity>)

    @Query("DELETE FROM subcategories")
    suspend fun clearAll()

    // Same "remote wins" cold-sync bootstrap pattern as CategoryDao.replaceAll().
    @Transaction
    suspend fun replaceAll(subcategories: List<SubcategoryEntity>) {
        clearAll()
        insertAll(subcategories)
    }

    @Query("DELETE FROM subcategories WHERE categoryType = :type AND categoryName = :categoryName AND name = :name")
    suspend fun deleteOne(type: String, categoryName: String, name: String)

    // Mirrors js/settings-managers.js's renameCategory() also renaming the
    // subcategories key — used by FinanceRepository.renameCategory() so a
    // category rename doesn't silently orphan its subcategories.
    @Query("UPDATE subcategories SET categoryName = :newName WHERE categoryType = :type AND categoryName = :oldName")
    suspend fun renameCategoryName(type: String, oldName: String, newName: String)

    // Mirrors js/settings-managers.js's deleteCategory() cascade.
    @Query("DELETE FROM subcategories WHERE categoryType = :type AND categoryName = :categoryName")
    suspend fun deleteAllForCategory(type: String, categoryName: String)

    @Query("SELECT COUNT(*) FROM subcategories WHERE categoryType = :type AND categoryName = :categoryName AND name = :name")
    suspend fun countOne(type: String, categoryName: String, name: String): Int

    @Query("SELECT COUNT(*) FROM subcategories")
    suspend fun count(): Int
}
