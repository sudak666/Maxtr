package ua.rytm.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ua.rytm.app.data.local.DebtEntity
import ua.rytm.app.data.local.DebtEntryEntity
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.ui.screens.debt.Debt
import ua.rytm.app.ui.screens.debt.DebtEntry

fun DebtEntryEntity.toDomain() = DebtEntry(id, amount, balance, date)
fun DebtEntry.toEntity(debtId: Long) = DebtEntryEntity(id, debtId, amount, balance, date)

class DebtRepository(private val db: RytmDatabase) {

    val debts: Flow<List<Debt>> = combine(db.debtDao().observeAll(), db.debtEntryDao().observeAll()) { debts, entries ->
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
            db.debtDao().insert(DebtEntity(id = System.currentTimeMillis(), name = "Кредит", note = "", currency = "грн", startAmount = 0.0, dueDate = ""))
        }
    }

    suspend fun addDebt(debt: Debt) {
        db.debtDao().insert(DebtEntity(debt.id, debt.name, debt.note, debt.currency, debt.startAmount, debt.dueDate))
    }

    suspend fun updateDebt(debt: Debt) {
        db.debtDao().update(DebtEntity(debt.id, debt.name, debt.note, debt.currency, debt.startAmount, debt.dueDate))
    }

    // Mirrors js/debt.js's deleteCurrentDebt(): drop the debt and every payment under it.
    suspend fun deleteDebt(id: Long) {
        db.debtDao().deleteById(id)
        db.debtEntryDao().deleteAllForDebt(id)
    }

    suspend fun addEntry(debtId: Long, entry: DebtEntry) {
        db.debtEntryDao().insert(entry.toEntity(debtId))
    }

    suspend fun updateEntry(debtId: Long, entry: DebtEntry) {
        db.debtEntryDao().update(entry.toEntity(debtId))
    }

    suspend fun deleteEntry(id: Long) {
        db.debtEntryDao().deleteById(id)
    }
}
