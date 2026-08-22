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
@Entity(tableName = "categories", primaryKeys = ["ownerUid", "profileId", "id"])
data class CategoryEntity(
    val id: String,
    val type: String, // TxType.name ("INCOME" or "EXPENSE")
    val name: String,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY name ASC")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY name ASC")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Same "remote wins" cold-sync bootstrap pattern as WalletDao.replaceAll() —
    // a real @Transaction so a crash mid-sync can't leave the table half-cleared.
    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>) {
        clearAll()
        insertAll(categories)
    }

    @Query("DELETE FROM categories WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("SELECT * FROM categories WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun getById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun count(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int

    @Query("SELECT COUNT(*) FROM categories WHERE ownerUid=:ownerUid AND profileId=:profileId AND type = :type AND name = :name")
    suspend fun countByTypeAndName(type: String, name: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int
}

// 1:1 with AppState.subcategories (js/state.js) — `Record<subKey(type,name), string[]>`
// (js/core.js's subKey(type,name) => `${type}:${name}`, confirmed by reading
// js/settings-managers.js's addSubcategory()/deleteSubcategory()). Composite
// key here (no separate id) since the PWA itself has no id concept for these
// either — same reasoning CategoriesSyncRepository's doc comment already gives
// for CategoryEntity.id being a purely local Room artifact.
@Entity(tableName = "subcategories", primaryKeys = ["ownerUid", "profileId", "categoryType", "categoryName", "name"])
data class SubcategoryEntity(
    val categoryType: String, // TxType.name
    val categoryName: String,
    val name: String,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface SubcategoryDao {
    @Query("SELECT * FROM subcategories WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY name ASC")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM subcategories WHERE ownerUid=:ownerUid AND profileId=:profileId ORDER BY name ASC")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<SubcategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(subcategory: SubcategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(subcategories: List<SubcategoryEntity>)

    @Query("DELETE FROM subcategories WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Same "remote wins" cold-sync bootstrap pattern as CategoryDao.replaceAll().
    @Transaction
    suspend fun replaceAll(subcategories: List<SubcategoryEntity>) {
        clearAll()
        insertAll(subcategories)
    }

    @Query("DELETE FROM subcategories WHERE ownerUid=:ownerUid AND profileId=:profileId AND categoryType = :type AND categoryName = :categoryName AND name = :name")
    suspend fun deleteOne(type: String, categoryName: String, name: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Mirrors js/settings-managers.js's renameCategory() also renaming the
    // subcategories key — used by FinanceRepository.renameCategory() so a
    // category rename doesn't silently orphan its subcategories.
    @Query("UPDATE subcategories SET categoryName = :newName WHERE ownerUid=:ownerUid AND profileId=:profileId AND categoryType = :type AND categoryName = :oldName")
    suspend fun renameCategoryName(type: String, oldName: String, newName: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Mirrors js/settings-managers.js's deleteCategory() cascade.
    @Query("DELETE FROM subcategories WHERE ownerUid=:ownerUid AND profileId=:profileId AND categoryType = :type AND categoryName = :categoryName")
    suspend fun deleteAllForCategory(type: String, categoryName: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("SELECT COUNT(*) FROM subcategories WHERE ownerUid=:ownerUid AND profileId=:profileId AND categoryType = :type AND categoryName = :categoryName AND name = :name")
    suspend fun countOne(type: String, categoryName: String, name: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int

    @Query("SELECT COUNT(*) FROM subcategories WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun count(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int
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
@Entity(tableName = "category_icons", primaryKeys = ["ownerUid", "profileId", "categoryName"])
data class CategoryIconEntity(
    val categoryName: String,
    val iconName: String,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface CategoryIconDao {
    @Query("SELECT * FROM category_icons WHERE ownerUid=:ownerUid AND profileId=:profileId")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<CategoryIconEntity>>

    @Query("SELECT * FROM category_icons WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<CategoryIconEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CategoryIconEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CategoryIconEntity>)

    @Query("DELETE FROM category_icons WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Same "remote wins" cold-sync bootstrap pattern as every other synced domain.
    @Transaction
    suspend fun replaceAll(entities: List<CategoryIconEntity>) {
        clearAll()
        insertAll(entities)
    }

    // Mirrors js/settings-managers.js's renameCategory()/deleteCategory()
    // moving/dropping AppState.categoryIcons[name] — see
    // FinanceRepository.renameCategory()/deleteCategory() for the callers.
    @Query("UPDATE category_icons SET categoryName = :newName WHERE ownerUid=:ownerUid AND profileId=:profileId AND categoryName = :oldName")
    suspend fun renameCategory(oldName: String, newName: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("DELETE FROM category_icons WHERE ownerUid=:ownerUid AND profileId=:profileId AND categoryName = :categoryName")
    suspend fun deleteForCategory(categoryName: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)
}
