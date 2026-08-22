package ua.rytm.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.DebtEntity
import ua.rytm.app.data.local.DebtEntryEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.ui.screens.debt.Debt
import ua.rytm.app.ui.screens.debt.DebtEntry

fun DebtEntryEntity.toDomain() = DebtEntry(id, amount, balance, date)
fun DebtEntry.toEntity(debtId: Long) = DebtEntryEntity(id, debtId, amount, balance, date)

class DebtRepository(private val db: RytmDatabase) {

    private val debtRows = RoomProfileScope.changes.flatMapLatest { db.debtDao().observeAll(it.ownerUid, it.profileId) }
    private val entryRows = RoomProfileScope.changes.flatMapLatest { db.debtEntryDao().observeAll(it.ownerUid, it.profileId) }
    val debts: Flow<List<Debt>> = combine(debtRows, entryRows) { debts, entries ->
        val byDebtId = entries.groupBy { it.debtId }
        debts.map { d ->
            Debt(
                id = d.id,
                name = d.name,
                note = d.note,
                currency = d.currency,
                startAmount = d.startAmount,
                dueDate = d.dueDate,
                // entry ids are timestamps assigned in insertion order, so sorting by id
                // recovers the PWA's chronological (push) order.
                entries = byDebtId[d.id].orEmpty().sortedBy { it.id }.map { it.toDomain() },
            )
        }
    }

    suspend fun seedIfEmpty() {
        if (db.debtDao().count() == 0) {
            db.debtDao().insert(DebtEntity(id = System.currentTimeMillis(), name = "Кредит", note = "", currency = "UAH", startAmount = 0.0, dueDate = ""))
        }
    }

    suspend fun addDebt(debt: Debt) {
        requireValidStoredAmount(debt.startAmount, "debt start amount")
        db.debtDao().insert(DebtEntity(debt.id, debt.name, debt.note, debt.currency, debt.startAmount, debt.dueDate))
    }

    suspend fun updateDebt(debt: Debt) {
        requireValidStoredAmount(debt.startAmount, "debt start amount")
        db.debtDao().update(DebtEntity(debt.id, debt.name, debt.note, debt.currency, debt.startAmount, debt.dueDate))
    }

    // Mirrors js/debt.js's deleteCurrentDebt(): drop the debt and every payment under it.
    suspend fun deleteDebt(id: Long) {
        db.debtDao().deleteById(id)
        db.debtEntryDao().deleteAllForDebt(id)
    }

    suspend fun addEntry(debtId: Long, entry: DebtEntry) {
        requireValidStoredAmount(entry.balance, "debt balance")
        db.debtEntryDao().insert(entry.toEntity(debtId))
    }

    suspend fun updateEntry(debtId: Long, entry: DebtEntry) {
        requireValidStoredAmount(entry.balance, "debt balance")
        db.debtEntryDao().update(entry.toEntity(debtId))
    }

    suspend fun deleteEntry(id: Long) {
        db.debtEntryDao().deleteById(id)
    }

    data class Snapshot(val debts: List<DebtEntity>, val entries: List<DebtEntryEntity>)
    suspend fun snapshot() = Snapshot(db.debtDao().getAllOnce(), db.debtEntryDao().getAllOnce())
    suspend fun restore(snapshot: Snapshot) = db.withTransaction {
        db.debtEntryDao().clearAll()
        db.debtDao().clearAll()
        db.debtDao().insertAll(snapshot.debts)
        db.debtEntryDao().insertAll(snapshot.entries)
    }
}
