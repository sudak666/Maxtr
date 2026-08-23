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
@Entity(tableName = "shift_types", primaryKeys = ["ownerUid", "profileId", "id"])
data class ShiftTypeEntity(
    val id: String,
    val name: String,
    val short: String,
    val code: String,
    val colorHex: Long,
    val amount: Double,
    val hours: Double,
    val isOff: Boolean,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

// One row per (date, shiftType) assignment — mirrors AppState.shifts[dateKey]: string[]
// as a normalized table instead of a CSV column, so DAO queries stay plain SQL.
@Entity(tableName = "shift_days", primaryKeys = ["ownerUid", "profileId", "dateKey", "shiftTypeId"])
data class ShiftDayEntity(
    val dateKey: String, // "yyyy-MM-dd"
    val shiftTypeId: String,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface ShiftTypeDao {
    @Query("SELECT * FROM shift_types WHERE ownerUid=:ownerUid AND profileId=:profileId")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<ShiftTypeEntity>>

    @Query("SELECT * FROM shift_types WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<ShiftTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(types: List<ShiftTypeEntity>)

    @Query("DELETE FROM shift_types WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Used only by the Firestore cold-sync bootstrap (ShiftsSyncRepository), same
    // reasoning as WalletDao.replaceAll().
    @Transaction
    suspend fun replaceAll(types: List<ShiftTypeEntity>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        clearAll(ownerUid, profileId)
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

    @Query("DELETE FROM shift_types WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = :id")
    suspend fun deleteById(id: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    @Query("SELECT COUNT(*) FROM shift_types WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun count(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Int
}

// Single-row auto-fill configuration. `anchorDate` uses ISO local-date text.
@Entity(tableName = "autofill_schedule", primaryKeys = ["ownerUid", "profileId", "id"])
data class AutoFillScheduleEntity(
    val id: Int = 0,
    val enabled: Boolean,
    val typeId: String,
    val pattern: String,
    val anchorDate: String,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Dao
interface AutoFillScheduleDao {
    @Query("SELECT * FROM autofill_schedule WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = 0")
    fun observe(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<AutoFillScheduleEntity?>

    @Query("SELECT * FROM autofill_schedule WHERE ownerUid=:ownerUid AND profileId=:profileId AND id = 0")
    suspend fun getOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): AutoFillScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AutoFillScheduleEntity)

    @Query("DELETE FROM autofill_schedule WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)
}

@Dao
interface ShiftDayDao {
    @Query("SELECT * FROM shift_days WHERE ownerUid=:ownerUid AND profileId=:profileId")
    fun observeAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): Flow<List<ShiftDayEntity>>

    @Query("SELECT * FROM shift_days WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun getAllOnce(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<ShiftDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<ShiftDayEntity>)

    @Query("DELETE FROM shift_days WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun clearAll(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Same "remote wins" cold-sync bootstrap pattern as ShiftTypeDao/WalletDao's
    // replaceAll() — a real @Transaction so a crash mid-sync can't leave the
    // table half-cleared.
    @Transaction
    suspend fun replaceAll(days: List<ShiftDayEntity>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        clearAll(ownerUid, profileId)
        insertAll(days)
    }

    @Query("DELETE FROM shift_days WHERE ownerUid=:ownerUid AND profileId=:profileId AND dateKey = :dateKey")
    suspend fun deleteForDate(dateKey: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Mirrors js/settings-managers.js's deleteShiftType() stripping the id from every calendar day.
    @Query("DELETE FROM shift_days WHERE ownerUid=:ownerUid AND profileId=:profileId AND shiftTypeId = :shiftTypeId")
    suspend fun deleteByShiftTypeId(shiftTypeId: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    /** Replaces the full assignment set for one day in a single call site (see ShiftsRepository.setShiftsForDay()). */
    @androidx.room.Transaction
    suspend fun setForDate(dateKey: String, shiftTypeIds: List<String>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        deleteForDate(dateKey, ownerUid, profileId)
        insertAll(shiftTypeIds.map { ShiftDayEntity(dateKey, it, ownerUid, profileId) })
    }

    // "yyyy-MM-" prefix match — mirrors js/calendar.js's clearCurrentMonth()/
    // applyTemplate() (`Object.keys(AppState.shifts).forEach(k=>{if(k.startsWith(p))...`).
    @Query("DELETE FROM shift_days WHERE ownerUid=:ownerUid AND profileId=:profileId AND dateKey LIKE :monthPrefix || '%'")
    suspend fun deleteForMonth(monthPrefix: String, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId)

    // Quick-fill's "Застосувати": wipe the visible month, then write the
    // pattern-generated set in one transaction — mirrors applyTemplate()'s
    // own clear-then-fill shape.
    @Transaction
    suspend fun applyTemplate(monthPrefix: String, days: List<ShiftDayEntity>, ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId) {
        deleteForMonth(monthPrefix, ownerUid, profileId)
        insertAll(days)
    }

    // Autofill only ever fills a day that has NO existing assignment
    // (js/calendar.js's processAutoFillShifts(): `if(!AppState.shifts[key])`)
    // — never overwrites a day the user already edited by hand.
    @Query("SELECT DISTINCT dateKey FROM shift_days WHERE ownerUid=:ownerUid AND profileId=:profileId")
    suspend fun getAllAssignedDateKeys(ownerUid: String = RoomProfileScope.ownerUid, profileId: String = RoomProfileScope.profileId): List<String>
}
