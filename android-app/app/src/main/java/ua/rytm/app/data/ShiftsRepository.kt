package ua.rytm.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.rytm.app.data.local.AutoFillScheduleEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.ShiftDayEntity
import ua.rytm.app.data.local.ShiftTypeEntity
import ua.rytm.app.ui.screens.shifts.AutoFillSchedule
import ua.rytm.app.ui.screens.shifts.SHIFT_PATTERN_CYCLES
import ua.rytm.app.ui.screens.shifts.SeedShiftTypes
import ua.rytm.app.ui.screens.shifts.ShiftType
import ua.rytm.app.ui.screens.shifts.daysForShiftPattern
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun ShiftTypeEntity.toDomain() = ShiftType(id, name, short, code, colorHex, amount, hours, isOff)
fun ShiftType.toEntity() = ShiftTypeEntity(id, name, short, code, colorHex, amount, hours, isOff)

fun AutoFillScheduleEntity.toDomain() = AutoFillSchedule(enabled, typeId, pattern, anchorDate)
fun AutoFillSchedule.toEntity() = AutoFillScheduleEntity(id = 0, enabled = enabled, typeId = typeId, pattern = pattern, anchorDate = anchorDate)

class ShiftsRepository(private val db: RytmDatabase, private val sync: ShiftsSyncRepository) {

    val shiftTypes: Flow<List<ShiftType>> = db.shiftTypeDao().observeAll().map { list -> list.map { it.toDomain() } }

    /** dateKey -> assigned shift type ids — mirrors AppState.shifts (js/state.js). */
    val shiftsByDate: Flow<Map<String, List<String>>> = db.shiftDayDao().observeAll().map { list ->
        list.groupBy({ it.dateKey }, { it.shiftTypeId })
    }

    val autoFillSchedule: Flow<AutoFillSchedule> = db.autoFillScheduleDao().observe().map { it?.toDomain() ?: AutoFillSchedule() }

    suspend fun seedIfEmpty() {
        if (db.shiftTypeDao().count() == 0) {
            db.shiftTypeDao().insertAll(SeedShiftTypes.types.map { it.toEntity() })
        }
    }

    /** Exact no-data defaults from js/core.js DEFAULT_SHIFT_TYPES, not legacy/demo types. */
    suspend fun seedFreshProfileDefaults() {
        db.shiftTypeDao().insertAll(
            listOf(
                ShiftTypeEntity("st_day", "Денна зміна", "День", "Д", 0xFF3B82F6, 0.0, 8.0, false),
                ShiftTypeEntity("st_night", "Нічна зміна", "Ніч", "Н", 0xFF8B5CF6, 0.0, 12.0, false),
                ShiftTypeEntity("st_off", "Вихідний", "Вих", "В", 0xFFF59E0B, 0.0, 0.0, true),
            ),
        )
    }

    suspend fun setShiftsForDay(uid: String, profileId: String, dateKey: String, shiftTypeIds: List<String>) {
        mutateDays(uid, profileId) { db.shiftDayDao().setForDate(dateKey, shiftTypeIds) }
    }

    // Quick-fill's "Застосувати" — mirrors js/calendar.js's applyTemplate():
    // wipe the given month, then write `typeId` on every day the pattern's
    // on/off cycle covers.
    suspend fun applyTemplate(uid: String, profileId: String, monthPrefix: String, daysInMonth: Int, typeId: String, pattern: String) {
        val entities = daysForShiftPattern(daysInMonth, pattern)
            .map { d -> ShiftDayEntity("$monthPrefix-" + d.toString().padStart(2, '0'), typeId) }
        mutateDays(uid, profileId) { db.shiftDayDao().applyTemplate(monthPrefix, entities) }
    }

    suspend fun clearMonth(uid: String, profileId: String, monthPrefix: String) {
        mutateDays(uid, profileId) { db.shiftDayDao().deleteForMonth(monthPrefix) }
    }

    suspend fun setAutoFillSchedule(uid: String, profileId: String, schedule: AutoFillSchedule, processDays: Boolean) {
        val oldSchedule = db.autoFillScheduleDao().getOnce()
        val oldDays = db.shiftDayDao().getAllOnce()
        try {
            db.autoFillScheduleDao().upsert(schedule.toEntity())
            if (processDays) processAutoFillShifts()
            sync.saveAutoFillAndDays(uid, profileId)
        } catch (e: Exception) {
            if (oldSchedule == null) db.autoFillScheduleDao().clearAll() else db.autoFillScheduleDao().upsert(oldSchedule)
            db.shiftDayDao().replaceAll(oldDays)
            throw e
        }
    }

    // Mirrors js/calendar.js's autoFillTypeForDate()/processAutoFillShifts():
    // walks from the schedule's anchor date (capped 60 days back) to today,
    // filling only days with NO existing assignment — never overwrites a
    // hand-edited day. Returns how many days were newly filled.
    suspend fun processAutoFillShifts(): Int {
        val schedule = db.autoFillScheduleDao().getOnce()?.toDomain() ?: return 0
        if (!schedule.enabled || schedule.typeId.isBlank() || schedule.anchorDate.isBlank()) return 0
        if (db.shiftTypeDao().getAllOnce().none { it.id == schedule.typeId }) return 0
        val (on, off) = SHIFT_PATTERN_CYCLES[schedule.pattern] ?: SHIFT_PATTERN_CYCLES.getValue("every")
        val period = on + off
        if (period <= 0) return 0

        val anchor = runCatching { LocalDate.parse(schedule.anchorDate) }.getOrNull() ?: return 0
        val today = LocalDate.now()
        val earliest = today.minusDays(60)
        var cursor = if (anchor < earliest) earliest else anchor
        val existing = db.shiftDayDao().getAllAssignedDateKeys().toHashSet()
        val newEntities = mutableListOf<ShiftDayEntity>()
        var guard = 0
        while (!cursor.isAfter(today) && guard < 60) {
            val key = cursor.toString()
            if (key !in existing) {
                val diffDays = ChronoUnit.DAYS.between(anchor, cursor)
                if (diffDays >= 0 && (diffDays % period) < on) newEntities += ShiftDayEntity(key, schedule.typeId)
            }
            cursor = cursor.plusDays(1)
            guard++
        }
        if (newEntities.isNotEmpty()) db.shiftDayDao().insertAll(newEntities)
        return newEntities.size
    }

    suspend fun addShiftType(uid: String, profileId: String, type: ShiftType) {
        mutateTypes(uid, profileId) { db.shiftTypeDao().insert(type.toEntity()) }
    }

    suspend fun updateShiftType(uid: String, profileId: String, type: ShiftType) {
        mutateTypes(uid, profileId) { db.shiftTypeDao().update(type.toEntity()) }
    }

    // Mirrors js/settings-managers.js's deleteShiftType(): delete the type, then
    // strip its id from every calendar day so counts/renders stay correct.
    suspend fun deleteShiftType(uid: String, profileId: String, id: String) {
        val oldTypes = db.shiftTypeDao().getAllOnce(); val oldDays = db.shiftDayDao().getAllOnce()
        try {
            db.shiftTypeDao().deleteById(id); db.shiftDayDao().deleteByShiftTypeId(id)
            sync.saveShiftTypesAndDays(uid, profileId)
        } catch (e: Exception) {
            db.shiftTypeDao().replaceAll(oldTypes); db.shiftDayDao().replaceAll(oldDays); throw e
        }
    }

    private suspend fun mutateDays(uid: String, profileId: String, block: suspend () -> Unit) {
        val old = db.shiftDayDao().getAllOnce()
        try { block(); sync.saveShiftDays(uid, profileId) }
        catch (e: Exception) { db.shiftDayDao().replaceAll(old); throw e }
    }

    private suspend fun mutateTypes(uid: String, profileId: String, block: suspend () -> Unit) {
        val old = db.shiftTypeDao().getAllOnce()
        try { block(); sync.saveShiftTypes(uid, profileId) }
        catch (e: Exception) { db.shiftTypeDao().replaceAll(old); throw e }
    }
}
