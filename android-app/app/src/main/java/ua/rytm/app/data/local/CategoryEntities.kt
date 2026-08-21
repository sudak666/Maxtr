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

// 1:1 with AppState.categoryIcons (js/state.js) — `Record<categoryName,
// iconName>`, a manual per-category icon override set via
// openCategoryIconPicker() (js/settings-managers.js). Keyed by category NAME
// only, not (type,name) or a CategoryEntity id — confirmed by reading
// js/core.js's categoryIcon(name): `AppState.categoryIcons[name]`, no type
// distinction at all (the same simplification BudgetEntity's own doc
// comment already flags for budgets — a real name collision across
// income/expense is possible in principle but the PWA itself doesn't guard
// against it either, so this doesn't regress anything). `iconName` stores
// the PWA's own icon-name string (one of window.ICON_NAMES, see
// CategoryColor.kt's PICKER_ICONS) so a value written by either platform
// round-trips meaningfully on the other, not an Android-only identifier.
@Entity(tableName = "category_icons")
data class CategoryIconEntity(
    @PrimaryKey val categoryName: String,
    val iconName: String,
)

@Dao
interface CategoryIconDao {
    @Query("SELECT * FROM category_icons")
    fun observeAll(): Flow<List<CategoryIconEntity>>

    @Query("SELECT * FROM category_icons")
    suspend fun getAllOnce(): List<CategoryIconEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CategoryIconEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CategoryIconEntity>)

    @Query("DELETE FROM category_icons")
    suspend fun clearAll()

    // Same "remote wins" cold-sync bootstrap pattern as every other synced domain.
    @Transaction
    suspend fun replaceAll(entities: List<CategoryIconEntity>) {
        clearAll()
        insertAll(entities)
    }

    // Mirrors js/settings-managers.js's renameCategory()/deleteCategory()
    // moving/dropping AppState.categoryIcons[name] — see
    // FinanceRepository.renameCategory()/deleteCategory() for the callers.
    @Query("UPDATE category_icons SET categoryName = :newName WHERE categoryName = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    @Query("DELETE FROM category_icons WHERE categoryName = :categoryName")
    suspend fun deleteForCategory(categoryName: String)
}
