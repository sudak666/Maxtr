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

// 1:1 with LEGACY_SHIFT_TYPES (js/core.js) — colorHex stored as Long (ARGB), matching WalletEntity's convention.
@Entity(tableName = "shift_types")
data class ShiftTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val short: String,
    val code: String,
    val colorHex: Long,
    val amount: Double,
    val hours: Double,
    val isOff: Boolean,
)

// One row per (date, shiftType) assignment — mirrors AppState.shifts[dateKey]: string[]
// as a normalized table instead of a CSV column, so DAO queries stay plain SQL.
@Entity(tableName = "shift_days", primaryKeys = ["dateKey", "shiftTypeId"])
data class ShiftDayEntity(
    val dateKey: String, // "yyyy-MM-dd"
    val shiftTypeId: String,
)

@Dao
interface ShiftTypeDao {
    @Query("SELECT * FROM shift_types")
    fun observeAll(): Flow<List<ShiftTypeEntity>>

    @Query("SELECT * FROM shift_types")
    suspend fun getAllOnce(): List<ShiftTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(types: List<ShiftTypeEntity>)

    @Query("DELETE FROM shift_types")
    suspend fun clearAll()

    // Used only by the Firestore cold-sync bootstrap (ShiftsSyncRepository), same
    // reasoning as WalletDao.replaceAll().
    @Transaction
    suspend fun replaceAll(types: List<ShiftTypeEntity>) {
        clearAll()
        insertAll(types)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(type: ShiftTypeEntity)

    // Insert-with-REPLACE does a delete+insert under the hood, which changes the row's
    // SQLite rowid and reorders observeAll()'s no-ORDER-BY result — a real bug hit while
    // testing edits (renaming a type moved it to the bottom of the list). A real UPDATE
    // statement preserves rowid/order, matching the PWA's stable AppState.shiftTypes order.
    @Update
    suspend fun update(type: ShiftTypeEntity)

    @Query("DELETE FROM shift_types WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM shift_types")
    suspend fun count(): Int
}

@Dao
interface ShiftDayDao {
    @Query("SELECT * FROM shift_days")
    fun observeAll(): Flow<List<ShiftDayEntity>>

    @Query("SELECT * FROM shift_days")
    suspend fun getAllOnce(): List<ShiftDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<ShiftDayEntity>)

    @Query("DELETE FROM shift_days")
    suspend fun clearAll()

    // Same "remote wins" cold-sync bootstrap pattern as ShiftTypeDao/WalletDao's
    // replaceAll() — a real @Transaction so a crash mid-sync can't leave the
    // table half-cleared.
    @Transaction
    suspend fun replaceAll(days: List<ShiftDayEntity>) {
        clearAll()
        insertAll(days)
    }

    @Query("DELETE FROM shift_days WHERE dateKey = :dateKey")
    suspend fun deleteForDate(dateKey: String)

    // Mirrors js/settings-managers.js's deleteShiftType() stripping the id from every calendar day.
    @Query("DELETE FROM shift_days WHERE shiftTypeId = :shiftTypeId")
    suspend fun deleteByShiftTypeId(shiftTypeId: String)

    /** Replaces the full assignment set for one day in a single call site (see ShiftsRepository.setShiftsForDay()). */
    @androidx.room.Transaction
    suspend fun setForDate(dateKey: String, shiftTypeIds: List<String>) {
        deleteForDate(dateKey)
        insertAll(shiftTypeIds.map { ShiftDayEntity(dateKey, it) })
    }
}
