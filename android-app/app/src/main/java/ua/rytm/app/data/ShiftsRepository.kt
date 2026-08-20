package ua.rytm.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.ShiftTypeEntity
import ua.rytm.app.ui.screens.shifts.SeedShiftTypes
import ua.rytm.app.ui.screens.shifts.ShiftType

fun ShiftTypeEntity.toDomain() = ShiftType(id, name, short, code, colorHex, amount, hours, isOff)
fun ShiftType.toEntity() = ShiftTypeEntity(id, name, short, code, colorHex, amount, hours, isOff)

class ShiftsRepository(private val db: RytmDatabase) {

    val shiftTypes: Flow<List<ShiftType>> = db.shiftTypeDao().observeAll().map { list -> list.map { it.toDomain() } }

    /** dateKey -> assigned shift type ids — mirrors AppState.shifts (js/state.js). */
    val shiftsByDate: Flow<Map<String, List<String>>> = db.shiftDayDao().observeAll().map { list ->
        list.groupBy({ it.dateKey }, { it.shiftTypeId })
    }

    suspend fun seedIfEmpty() {
        if (db.shiftTypeDao().count() == 0) {
            db.shiftTypeDao().insertAll(SeedShiftTypes.types.map { it.toEntity() })
        }
    }

    suspend fun setShiftsForDay(dateKey: String, shiftTypeIds: List<String>) {
        db.shiftDayDao().setForDate(dateKey, shiftTypeIds)
    }

    suspend fun addShiftType(type: ShiftType) {
        db.shiftTypeDao().insert(type.toEntity())
    }

    suspend fun updateShiftType(type: ShiftType) {
        db.shiftTypeDao().update(type.toEntity())
    }

    // Mirrors js/settings-managers.js's deleteShiftType(): delete the type, then
    // strip its id from every calendar day so counts/renders stay correct.
    suspend fun deleteShiftType(id: String) {
        db.shiftTypeDao().deleteById(id)
        db.shiftDayDao().deleteByShiftTypeId(id)
    }
}
