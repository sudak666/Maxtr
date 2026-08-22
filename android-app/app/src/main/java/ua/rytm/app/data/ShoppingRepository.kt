package ua.rytm.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.ShoppingItemEntity
import ua.rytm.app.ui.screens.shopping.ShoppingItem

fun ShoppingItemEntity.toDomain() = ShoppingItem(id = id, name = name, qty = qty, done = done, createdAt = createdAt)
fun ShoppingItem.toEntity() = ShoppingItemEntity(id = id, name = name, qty = qty, done = done, createdAt = createdAt)

class ShoppingRepository(private val db: RytmDatabase) {

    val items: Flow<List<ShoppingItem>> = db.shoppingDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun seedIfEmpty() {
        // The PWA starts with an empty shopping list.
    }

    suspend fun addItem(name: String, qty: Int) {
        db.shoppingDao().upsert(ShoppingItemEntity(id = java.util.UUID.randomUUID().toString(), name = name, qty = qty, done = false, createdAt = System.currentTimeMillis()))
    }

    suspend fun setDone(item: ShoppingItem, done: Boolean) {
        db.shoppingDao().upsert(item.copy(done = done).toEntity())
    }

    suspend fun delete(id: String) {
        db.shoppingDao().deleteById(id)
    }

    suspend fun clearBought() {
        db.shoppingDao().deleteBought()
    }

    suspend fun snapshot(): List<ShoppingItemEntity> = db.shoppingDao().getAllOnce()
    suspend fun restore(snapshot: List<ShoppingItemEntity>) = db.shoppingDao().replaceAll(snapshot)
}
